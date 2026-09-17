package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamPriceServiceTest {

    private static final String ITEM_NAME = "AK-47 | Redline (Field-Tested)";
    private static final String FEED_JSON = "{\"AK-47 | Redline (Field-Tested)\":{\"last_24h\":43.56," +
            "\"last_7d\":42.80,\"last_30d\":43.51,\"last_90d\":43.91}}";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MarketPriceSnapshotRepository marketPriceSnapshotRepository;

    @Mock
    private ItemRepository itemRepository;

    private SteamPriceService steamPriceService;
    private Item testItem;

    @BeforeEach
    void setUp() {
        steamPriceService = new SteamPriceService(
                restTemplate,
                new ObjectMapper(),
                marketPriceSnapshotRepository,
                itemRepository
        );
        ReflectionTestUtils.setField(steamPriceService, "feedCheckIntervalMs", 7_200_000L);

        testItem = new Item();
        testItem.setId(1L);
        testItem.setItemName(ITEM_NAME);
        testItem.setMarketHashName(ITEM_NAME);
        testItem.setQuantity(1);
        testItem.setSteamFoundCheck(true);
    }

    @Test
    void loadsLast24hPriceAndStoresOneRealSnapshot() {
        arrangeSuccessfulFeed(List.of(testItem), false);

        steamPriceService.refreshPriceCacheIfNeeded();

        BigDecimal currentPrice = steamPriceService.fetchCurrentPrice(ITEM_NAME);
        assertEquals(new BigDecimal("40.08"), currentPrice);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketPriceSnapshot>> snapshotsCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketPriceSnapshotRepository).saveAll(snapshotsCaptor.capture());
        MarketPriceSnapshot snapshot = snapshotsCaptor.getValue().getFirst();
        assertEquals(ITEM_NAME, snapshot.getMarketHashName());
        assertEquals(new BigDecimal("43.56"), snapshot.getPriceUsd());
        assertEquals(new BigDecimal("40.08"), snapshot.getPriceEur());
        assertEquals(Instant.parse("2026-09-16T08:10:59Z"), snapshot.getObservedAt());
        assertEquals(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, snapshot.getSource());
    }

    @Test
    void sendsConditionalHeadersAndDoesNotDuplicateAnUnchangedFeed() {
        ReflectionTestUtils.setField(steamPriceService, "feedCheckIntervalMs", 0L);
        ResponseEntity<byte[]> notModified = new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_MODIFIED);
        when(restTemplate.exchange(
                eq("https://prices.csgotrader.app/latest/steam.json"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenReturn(successfulFeedResponse(), notModified);
        when(restTemplate.getForEntity("https://open.er-api.com/v6/latest/USD", String.class))
                .thenReturn(ResponseEntity.ok("{\"rates\":{\"EUR\":0.92}}"));
        when(itemRepository.findAllSteamTrackedItems()).thenReturn(List.of(testItem));
        when(marketPriceSnapshotRepository.existsByMarketHashNameAndSourceAndSourceVersion(
                ITEM_NAME,
                MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                "feed-v1"
        )).thenReturn(false);

        steamPriceService.refreshPriceCacheIfNeeded();
        steamPriceService.refreshPriceCacheIfNeeded();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).exchange(
                eq("https://prices.csgotrader.app/latest/steam.json"),
                eq(HttpMethod.GET),
                requestCaptor.capture(),
                eq(byte[].class)
        );
        HttpHeaders secondRequestHeaders = requestCaptor.getAllValues().get(1).getHeaders();
        assertTrue(secondRequestHeaders.getIfNoneMatch().contains("\"feed-v1\""));
        assertTrue(secondRequestHeaders.getIfModifiedSince() > 0);
        verify(marketPriceSnapshotRepository, times(1)).saveAll(any());
    }

    @Test
    void doesNotStoreDuplicateSnapshotForAnotherUserTrackingTheSameItem() {
        Item secondUsersItem = new Item();
        secondUsersItem.setId(2L);
        secondUsersItem.setMarketHashName(ITEM_NAME);
        secondUsersItem.setSteamFoundCheck(true);
        arrangeSuccessfulFeed(List.of(testItem, secondUsersItem), true);

        steamPriceService.refreshPriceCacheIfNeeded();

        verify(marketPriceSnapshotRepository, never()).saveAll(any());
        verify(itemRepository).saveAll(any());
    }

    @Test
    void missingItemDoesNotFallBackToSteam() {
        arrangeSuccessfulFeed(List.of(), false);

        steamPriceService.refreshPriceCacheIfNeeded();

        assertTrue(steamPriceService.tryFetchPrice("Unknown item").isEmpty());
        assertFalse(steamPriceService.validateItemOnSteam("Unknown item"));
        verify(restTemplate, times(1)).exchange(
                eq("https://prices.csgotrader.app/latest/steam.json"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    @Test
    void updatePricesUsesCached24hAverageWithoutWritingLegacyHistory() {
        arrangeSuccessfulFeed(List.of(), false);
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(testItem));

        steamPriceService.refreshPriceCacheIfNeeded();
        steamPriceService.updatePricesForItems(List.of(1L));

        assertEquals(new BigDecimal("40.08"), testItem.getPrice());
        verify(itemRepository).saveAll(List.of(testItem));
    }

    @Test
    void manualUpdateBypassesTheNormalFeedCheckInterval() {
        arrangeSuccessfulFeed(List.of(testItem), false);

        steamPriceService.refreshPriceCacheIfNeeded();
        steamPriceService.forceUpdateAllTrackedItems();

        verify(restTemplate, times(2)).exchange(
                eq("https://prices.csgotrader.app/latest/steam.json"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    private void arrangeSuccessfulFeed(List<Item> trackedItems, boolean snapshotAlreadyExists) {
        when(restTemplate.exchange(
                eq("https://prices.csgotrader.app/latest/steam.json"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenReturn(successfulFeedResponse());
        when(restTemplate.getForEntity("https://open.er-api.com/v6/latest/USD", String.class))
                .thenReturn(ResponseEntity.ok("{\"rates\":{\"EUR\":0.92}}"));
        when(itemRepository.findAllSteamTrackedItems()).thenReturn(trackedItems);
        if (!trackedItems.isEmpty()) {
            when(marketPriceSnapshotRepository.existsByMarketHashNameAndSourceAndSourceVersion(
                    anyString(),
                    eq(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H),
                    eq("feed-v1")
            )).thenReturn(snapshotAlreadyExists);
        }
    }

    private ResponseEntity<byte[]> successfulFeedResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"feed-v1\"");
        headers.setLastModified(Instant.parse("2026-09-16T08:10:59Z").toEpochMilli());
        return new ResponseEntity<>(FEED_JSON.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
}
