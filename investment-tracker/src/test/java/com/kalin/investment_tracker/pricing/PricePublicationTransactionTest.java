package com.kalin.investment_tracker.pricing;

import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.item.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction proxies/commits: a mocked writer would not detect early cache publication. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PriceFeedService.class, MarketPricePersistenceService.class, PriceRefreshCooldown.class, PricePublicationTransactionTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PricePublicationTransactionTest {
    @TestConfiguration static class Beans {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC); }
        @Bean PriceFeedProperties properties() { return new PriceFeedProperties(); }
    }
    @Autowired PriceFeedService service;
    @Autowired ItemRepository items;
    @Autowired MarketPriceSnapshotRepository snapshots;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean CsgotraderFeedClient client;
    @MockitoBean UsdToEurRateService currency;
    @MockitoSpyBean MarketPricePersistenceService persistence;
    private String name;
    private Item item;

    @BeforeEach void setup() {
        snapshots.deleteAll(); name = UUID.randomUUID().toString();
        item = new Item(); item.setMarketHashName(name); item.setItemName(name);
        item.setSteamFoundCheck(true); item.setPrice(BigDecimal.ONE); item = items.saveAndFlush(item);
    }

    @Test void cacheRemainsOldThroughBeforeCommitAndPublishesOnlyAfterDatabaseCommit() throws Exception {
        arrange("first", "10", Instant.parse("2026-10-20T08:00:00Z"));
        service.forceRefreshPriceCache(); PriceFeedSnapshot first = service.getCachedPublication();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { assertSame(first, service.getCachedPublication()); }
            });
            return result;
        }).when(persistence).persistFeed(any());
        arrange("second", "20", Instant.parse("2026-10-21T08:00:00Z"));
        service.forceRefreshPriceCache();
        assertEquals(new BigDecimal("18.40"), service.getCachedItemPriceData(name).currentPrice);
        assertEquals(new BigDecimal("18.40"), items.findById(item.getId()).orElseThrow().getPrice());
        assertEquals(2, historyCount());
    }

    @Test void failureAtCommitKeepsPreviousCacheAndRollsBackBothPriceAndHistory() throws Exception {
        arrange("valid", "10", Instant.parse("2026-10-20T08:00:00Z")); service.forceRefreshPriceCache();
        PriceFeedSnapshot first = service.getCachedPublication();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("simulated commit failure"); }
            });
            return result;
        }).when(persistence).persistFeed(any());
        arrange("must-rollback", "20", Instant.parse("2026-10-21T08:00:00Z"));
        assertThrows(ExternalServiceException.class, service::forceRefreshPriceCache);
        assertSame(first, service.getCachedPublication());
        assertEquals(new BigDecimal("9.20"), items.findById(item.getId()).orElseThrow().getPrice());
        assertEquals(1, historyCount());
    }

    @Test void accidentalDownloadInsideUserTransactionFailsBeforeAnyNetworkOrPersistenceWork() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, service::refreshPriceCacheIfNeeded);
            assertNull(service.getCachedPublication());
            assertNull(service.getCachedItemPriceData(name));
        });
        verifyNoInteractions(client, currency);
    }

    private void arrange(String version, String usd, Instant observedAt) throws Exception {
        when(client.download(any(), anyBoolean())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must not hold a DB transaction");
            return ResponseEntity.ok(new byte[0]);
        });
        when(client.readPublication(any())).thenReturn(new CsgotraderFeedClient.Publication(
                Map.of(name, new BigDecimal(usd)), version, null, observedAt.toEpochMilli()));
        when(currency.getQuote(any())).thenReturn(new UsdToEurRateService.Quote(new BigDecimal("0.92"), null));
    }
    private long historyCount() {
        return snapshots.findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                name, MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, Instant.EPOCH).size();
    }
}
