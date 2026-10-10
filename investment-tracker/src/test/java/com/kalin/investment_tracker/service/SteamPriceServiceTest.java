package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.model.MarketPriceWriteLock;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.MarketPriceWriteLockRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SteamPriceServiceTest {
    private static final String NAME = "AK-47 | Redline (Field-Tested)";
    private static final String FEED = "{\"" + NAME + "\":{\"last_24h\":43.56,\"last_7d\":42.8,\"last_30d\":43.51,\"last_90d\":43.91}}";
    private static final String URL = "https://prices.csgotrader.app/latest/steam.json";
    private static final String FX = "https://open.er-api.com/v6/latest/USD";
    private static final Instant PUBLICATION = Instant.parse("2026-09-16T08:10:59Z");
    @Mock RestTemplate http;
    @Mock MarketPriceSnapshotRepository snapshots;
    @Mock MarketPriceWriteLockRepository writeLocks;
    @Mock ItemRepository items;
    private final List<MarketPriceSnapshot> stored = new ArrayList<>();
    private final List<Item> tracked = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private PriceFeedProperties settings;
    private SteamPriceService service;

    @BeforeEach void setUp() {
        settings = new PriceFeedProperties();
        service = newService();
        lenient().when(items.findDistinctTrackedMarketHashNames()).thenAnswer(call -> tracked.stream().map(Item::getMarketHashName).distinct().toList());
        lenient().when(items.findDistinctTrackedMarketHashNamesByIds(any())).thenAnswer(call -> tracked.stream()
                .filter(item -> ((Collection<?>) call.getArgument(0)).contains(item.getId())).map(Item::getMarketHashName).distinct().toList());
        lenient().when(items.updateTrackedPrice(anyString(), any())).thenAnswer(call -> {
            tracked.stream().filter(item -> item.getMarketHashName().equals(call.getArgument(0)))
                    .forEach(item -> item.setPrice(call.getArgument(1)));
            return tracked.size();
        });
        lenient().when(items.updateSelectedTrackedPrice(anyString(), any(), any())).thenAnswer(call -> {
            tracked.stream().filter(item -> ((Collection<?>) call.getArgument(2)).contains(item.getId()))
                    .forEach(item -> item.setPrice(call.getArgument(1)));
            return 1;
        });
        lenient().when(snapshots.findFirstBySourceAndSourceVersionOrderByIdAsc(anyString(), anyString()))
                .thenAnswer(call -> stored.stream().filter(s -> s.getSourceVersion().equals(call.getArgument(1))).findFirst());
        lenient().when(writeLocks.lockSource(anyString()))
                .thenReturn(Optional.of(new MarketPriceWriteLock(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H)));
        lenient().when(snapshots.findLatestObservedAtBySource(anyString()))
                .thenAnswer(call -> stored.stream().map(MarketPriceSnapshot::getObservedAt).max(Instant::compareTo).orElse(null));
        lenient().when(snapshots.findFirstBySourceOrderByObservedAtDescIdDesc(anyString()))
                .thenAnswer(call -> stored.isEmpty() ? Optional.empty() : Optional.of(stored.getLast()));
        lenient().when(snapshots.findBySourceAndObservationDateAndMarketHashNameIn(anyString(), any(), any()))
                .thenAnswer(call -> stored.stream().filter(s -> s.getObservationDate().equals(call.getArgument(1))
                        && ((Collection<?>) call.getArgument(2)).contains(s.getMarketHashName())).toList());
        lenient().when(snapshots.saveAllAndFlush(any())).thenAnswer(call -> {
            List<MarketPriceSnapshot> batch = call.getArgument(0);
            for (MarketPriceSnapshot snapshot : batch) {
                if (!stored.contains(snapshot)) {
                    snapshot.setId((long) stored.size() + 1);
                    stored.add(snapshot);
                }
            }
            return batch;
        });
    }
    private SteamPriceService newService() {
        return new SteamPriceService(http, new ObjectMapper(), new MarketPricePersistenceService(items, snapshots, writeLocks), settings, clock);
    }

    @Test void loadsLast24hPriceAndStoresOneRealSnapshot() {
        arrangeFeed(); track(1L);
        service.refreshPriceCacheIfNeeded();
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
        MarketPriceSnapshot snapshot = stored.getFirst();
        assertEquals(0, new BigDecimal("43.56").compareTo(snapshot.getPriceUsd()));
        assertEquals(new BigDecimal("40.08"), snapshot.getPriceEur());
        assertEquals(PUBLICATION, snapshot.getObservedAt());
        assertTrue(snapshot.getSourceVersion().startsWith("published-1789546259000-"));
        assertEquals(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, snapshot.getSource());
    }

    @Test void checksPublicationDateAndDoesNotDuplicateAnUnchangedFeed() {
        arrangeRate(); track(1L); settings.setCheckIntervalMs(1);
        feedCall().thenReturn(response(PUBLICATION), new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_MODIFIED));
        service.refreshPriceCacheIfNeeded(); clock.advance(2);
        service.refreshPriceCacheIfNeeded();
        ArgumentCaptor<HttpEntity<?>> requests = requestCaptor();
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), requests.capture(), eq(byte[].class));
        HttpHeaders headers = requests.getAllValues().get(1).getHeaders();
        assertTrue(headers.getIfNoneMatch().isEmpty());
        assertEquals(PUBLICATION.toEpochMilli(), headers.getIfModifiedSince());
        assertEquals(1, stored.size());
    }

    @Test void recordsNewObservationWhenUnchangedPricesAreRepublishedOnAnotherDay() {
        arrangeRate(); track(1L);
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(86400)));
        service.refreshPriceCacheIfNeeded(); service.forceUpdateAllTrackedItems();
        assertEquals(2, stored.size());
        assertEquals(stored.get(0).getPriceUsd(), stored.get(1).getPriceUsd());
        assertNotEquals(stored.get(0).getSourceVersion(), stored.get(1).getSourceVersion());
        assertEquals(PUBLICATION.plusSeconds(86400), stored.get(1).getObservedAt());
        ArgumentCaptor<HttpEntity<?>> requests = requestCaptor();
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), requests.capture(), eq(byte[].class));
        assertTrue(requests.getAllValues().get(1).getHeaders().getIfNoneMatch().isEmpty());
        assertEquals(-1, requests.getAllValues().get(1).getHeaders().getIfModifiedSince());
    }

    @Test void manualRefreshDoesNotDuplicateTheSamePublication() {
        arrangeFeed(); track(1L);
        service.refreshPriceCacheIfNeeded(); service.forceUpdateAllTrackedItems();
        assertEquals(1, stored.size());
        verify(http, times(1)).getForEntity(FX, String.class);
    }

    @Test void sameDayRepublicationUpdatesOneDailyObservationEvenWhenPriceIsUnchanged() {
        arrangeRate(); track(1L);
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(10801)));
        service.refreshPriceCacheIfNeeded();
        Long originalId = stored.getFirst().getId();
        assertEquals(1, service.forceUpdateAllTrackedItems());
        assertEquals(1, stored.size());
        assertEquals(originalId, stored.getFirst().getId());
        assertEquals(PUBLICATION.plusSeconds(10801), stored.getFirst().getObservedAt());
        assertEquals(new BigDecimal("40.08"), stored.getFirst().getPriceEur());
    }

    @Test void sameDayNewPriceReplacesTheDailyPointAndCurrentPrice() {
        arrangeRate(); track(1L);
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(3600), FEED.replace("43.56", "100")));
        service.refreshPriceCacheIfNeeded(); service.forceUpdateAllTrackedItems();
        assertEquals(1, stored.size());
        assertEquals(new BigDecimal("92.00"), stored.getFirst().getPriceEur());
        assertEquals(new BigDecimal("92.00"), tracked.getFirst().getPrice());
        assertEquals(new BigDecimal("92.00"), service.fetchCurrentPrice(NAME));
    }

    @Test void restartRejectsAnOlderProviderPublicationBeforeFetchingCurrency() {
        arrangeRate(); track(1L);
        feedCall().thenReturn(response(PUBLICATION.plusSeconds(3600)), response(PUBLICATION));
        service.refreshPriceCacheIfNeeded();
        service = newService();
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(1, stored.size());
        assertEquals(PUBLICATION.plusSeconds(3600), stored.getFirst().getObservedAt());
        assertEquals(new BigDecimal("40.08"), tracked.getFirst().getPrice());
        verify(http, times(1)).getForEntity(FX, String.class);
    }

    @Test void restartRevalidatesOneStaleCdnCopyAndReusesTheLatestStoredPublication() {
        arrangeRate(); track(1L);
        Instant latest = PUBLICATION.plusSeconds(3600);
        feedCall().thenReturn(response(latest), response(PUBLICATION), response(latest));
        service.refreshPriceCacheIfNeeded();
        service = newService(); service.refreshPriceCacheIfNeeded();
        assertEquals(1, stored.size());
        assertEquals(latest, stored.getFirst().getObservedAt());
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
        verify(http, times(1)).getForEntity(FX, String.class);
        ArgumentCaptor<HttpEntity<?>> requests = requestCaptor();
        verify(http, times(3)).exchange(eq(URL), eq(HttpMethod.GET), requests.capture(), eq(byte[].class));
        assertEquals(PUBLICATION.toEpochMilli(), requests.getAllValues().getLast().getHeaders().getIfModifiedSince());
    }

    @Test void manualRefreshReportsAProviderFailureWithoutLeakingItsResponse() {
        feedCall().thenThrow(new IllegalStateException("Provider unavailable: confidential response"));
        var error = assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals("Price feed refresh failed; previous data was retained", error.getMessage());
        assertTrue(stored.isEmpty());
    }

    @Test void doesNotStoreDuplicateSnapshotForAnotherUserTrackingTheSameItem() {
        arrangeFeed(); track(1L); track(2L);
        service.refreshPriceCacheIfNeeded();
        assertEquals(1, stored.size());
        assertEquals(new BigDecimal("40.08"), tracked.get(0).getPrice());
        assertEquals(tracked.get(0).getPrice(), tracked.get(1).getPrice());
    }

    @Test void missingItemDoesNotFallBackToSteam() {
        arrangeFeed(); service.refreshPriceCacheIfNeeded();
        assertTrue(service.tryFetchPrice("Unknown item").isEmpty());
        assertFalse(service.validateItemOnSteam("Unknown item"));
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void updatePricesUsesCached24hAverageWithoutWritingLegacyHistory() {
        arrangeFeed(); service.refreshPriceCacheIfNeeded(); track(1L);
        service.updatePricesForItems(List.of(1L));
        assertEquals(new BigDecimal("40.08"), tracked.getFirst().getPrice());
        verify(items).updateSelectedTrackedPrice(NAME, new BigDecimal("40.08"), List.of(1L));
        verify(items, never()).saveAll(any());
        assertEquals(1, stored.size());
    }

    @Test void manualUpdateBypassesTheNormalFeedCheckInterval() {
        arrangeFeed(); service.refreshPriceCacheIfNeeded(); service.forceUpdateAllTrackedItems();
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void malformedFeedRetainsCacheAndLimitsRepeatedFailures() {
        arrangeRate(); feedCall().thenReturn(response(PUBLICATION), ResponseEntity.ok("[]".getBytes(StandardCharsets.UTF_8)));
        service.refreshPriceCacheIfNeeded();
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
        clock.advance(60001);
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        verify(http, times(3)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void honoursProviderRetryAfterEvenForManualAttempts() {
        HttpHeaders headers = new HttpHeaders(); headers.set(HttpHeaders.RETRY_AFTER, "120");
        feedCall().thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Rate limited", headers, new byte[0], StandardCharsets.UTF_8));
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        clock.advance(60001);
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
        clock.advance(60001);
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void unavailableCurrencyWithoutVerifiedFallbackDoesNotInventPrices() {
        feedCall().thenReturn(response(PUBLICATION));
        when(http.getForEntity(FX, String.class)).thenThrow(new IllegalStateException()); track(1L);
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertNull(service.getItemPriceData(NAME));
        assertTrue(stored.isEmpty());
        verify(items, never()).updateTrackedPrice(anyString(), any());
    }

    @Test void newPublicationCanReusePreviouslyVerifiedCurrencyWhenFxIsOffline() {
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(86400)));
        when(http.getForEntity(FX, String.class)).thenReturn(ResponseEntity.ok("{\"rates\":{\"EUR\":0.92}}"))
                .thenThrow(new IllegalStateException()); track(1L);
        service.refreshPriceCacheIfNeeded(); service.forceUpdateAllTrackedItems();
        assertEquals(2, stored.size());
        assertEquals(stored.getFirst().getUsdToEurRate(), stored.getLast().getUsdToEurRate());
    }

    @Test void restartReusesRecordedPublicationCurrencyAndDoesNotDuplicateHistory() {
        arrangeFeed(); track(1L); service.refreshPriceCacheIfNeeded();
        service = newService(); service.refreshPriceCacheIfNeeded();
        assertEquals(1, stored.size());
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
        verify(http, times(1)).getForEntity(FX, String.class);
    }

    @Test void persistenceFailureDoesNotPublishUncommittedFeed() {
        arrangeRate(); track(1L);
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(86400), FEED.replace("43.56", "100")));
        service.refreshPriceCacheIfNeeded();
        doThrow(new IllegalStateException("Database unavailable")).when(snapshots).saveAllAndFlush(any());
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
    }

    @Test void stalePublicationCannotOverwriteNewerCache() {
        arrangeRate(); feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.minusSeconds(1)));
        service.refreshPriceCacheIfNeeded();
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
    }

    @Test void changedValuesWithSamePublicationIdentityAreRejected() {
        arrangeRate(); feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION, FEED.replace("43.56", "100")));
        service.refreshPriceCacheIfNeeded();
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
    }

    @Test void gzipIsDecodedAndOversizedExpansionIsRejected() throws Exception {
        arrangeRate();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write(FEED.getBytes(StandardCharsets.UTF_8)); }
        feedCall().thenReturn(new ResponseEntity<>(compressed.toByteArray(), response(PUBLICATION).getHeaders(), HttpStatus.OK));
        service.refreshPriceCacheIfNeeded();
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
        settings.setMaximumDecodedBytes(8);
        assertThrows(ExternalServiceException.class, service::forceUpdateAllTrackedItems);
        assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
    }

    @Test void publicationWithoutHeadersRetainsObservationTimeAcrossRestart() {
        arrangeRate(); track(1L); feedCall().thenReturn(ResponseEntity.ok(FEED.getBytes(StandardCharsets.UTF_8)));
        service.refreshPriceCacheIfNeeded(); Instant firstTime = stored.getFirst().getObservedAt();
        clock.advance(86400000); service = newService(); service.refreshPriceCacheIfNeeded();
        assertEquals(1, stored.size());
        assertEquals(firstTime, stored.getFirst().getObservedAt());
        verify(http, times(1)).getForEntity(FX, String.class);
    }

    @Test void concurrentColdReadersShareOneDownload() throws Exception {
        arrangeRate();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        feedCall().thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Download release timed out");
            return response(PUBLICATION);
        });
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<BigDecimal> first = workers.submit(() -> service.fetchCurrentPrice(NAME));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<BigDecimal> second = workers.submit(() -> { secondStarted.countDown(); return service.fetchCurrentPrice(NAME); });
            try { assertTrue(secondStarted.await(5, TimeUnit.SECONDS)); }
            finally { release.countDown(); }
            assertEquals(new BigDecimal("40.08"), first.get(5, TimeUnit.SECONDS));
            assertEquals(first.get(), second.get(5, TimeUnit.SECONDS));
        }
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void warmReadersSeePreviousCompleteCacheWhileRefreshRuns() throws Exception {
        arrangeRate();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        feedCall().thenReturn(response(PUBLICATION)).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Refresh release timed out");
            return response(PUBLICATION.plusSeconds(86400), FEED.replace("43.56", "100"));
        });
        service.refreshPriceCacheIfNeeded();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<Integer> future = worker.submit(service::forceUpdateAllTrackedItems);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(new BigDecimal("40.08"), service.fetchCurrentPrice(NAME));
            } finally { release.countDown(); }
            future.get(5, TimeUnit.SECONDS);
        }
        assertEquals(new BigDecimal("92.00"), service.fetchCurrentPrice(NAME));
    }

    @Test void bulkPricesUseOnePublicationAndExcludeMissingNames() {
        arrangeFeed();
        var prices = service.getCurrentPrices(List.of(NAME, "Unknown item"));
        assertEquals(Map.of(NAME, new BigDecimal("40.08")), prices);
        assertThrows(UnsupportedOperationException.class, () -> prices.put(NAME, BigDecimal.ZERO));
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void bulkPriceOutageIsNotReportedAsMissingItemPrices() {
        feedCall().thenThrow(new IllegalStateException("offline"));
        assertThrows(ExternalServiceException.class, () -> service.getCurrentPrices(List.of(NAME)));
        assertThrows(ExternalServiceException.class, () -> service.getCurrentPrices(List.of(NAME)));
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test void emptyBulkRequestDoesNotDownloadAFeed() {
        assertTrue(service.getCurrentPrices(List.of()).isEmpty());
        verifyNoInteractions(http);
    }

    @Test void returnedBulkValuationDoesNotChangeWhenAnotherPublicationArrives() {
        arrangeRate();
        feedCall().thenReturn(response(PUBLICATION), response(PUBLICATION.plusSeconds(86400), FEED.replace("43.56", "100")));
        var first = service.getCurrentPrices(List.of(NAME));
        service.forceUpdateAllTrackedItems();
        assertEquals(new BigDecimal("40.08"), first.get(NAME));
        assertEquals(new BigDecimal("92.00"), service.getCurrentPrices(List.of(NAME)).get(NAME));
    }

    private void arrangeFeed() { arrangeRate(); feedCall().thenReturn(response(PUBLICATION)); }
    private void arrangeRate() { when(http.getForEntity(FX, String.class)).thenReturn(ResponseEntity.ok("{\"rates\":{\"EUR\":0.92}}")); }
    private org.mockito.stubbing.OngoingStubbing<ResponseEntity<byte[]>> feedCall() {
        return when(http.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)));
    }
    private void track(long id) {
        Item item = new Item(); item.setId(id); item.setMarketHashName(NAME); item.setSteamFoundCheck(true); tracked.add(item);
    }
    private ResponseEntity<byte[]> response(Instant date) { return response(date, FEED); }
    private ResponseEntity<byte[]> response(Instant date, String json) {
        HttpHeaders headers = new HttpHeaders(); headers.setETag("\"feed-v1\""); headers.setLastModified(date.toEpochMilli());
        return new ResponseEntity<>(json.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<HttpEntity<?>> requestCaptor() { return (ArgumentCaptor) ArgumentCaptor.forClass(HttpEntity.class); }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");
        void advance(long ms) { now = now.plusMillis(ms); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
