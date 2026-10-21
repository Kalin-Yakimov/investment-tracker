package com.kalin.investment_tracker.mysql;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.migration.DatabaseMigrationTool;
import com.kalin.investment_tracker.pricing.*;
import com.kalin.investment_tracker.tracking.*;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production migrations, unique indexes, repeatable-read snapshots and real InnoDB locks. */
@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TrackedItemService.class, TrackedItemCreationService.class, MarketPricePersistenceService.class,
        com.kalin.investment_tracker.validation.ResourceLimitsProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MysqlItemConsistencyTest {
    private static final String URL = System.getenv("MYSQL_TEST_URL");
    private static final String USER = System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
    private static final Instant OBSERVED = Instant.parse("2026-10-20T08:00:00Z");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost|mysql):[0-9]+/investment_test_[a-z0-9_]+")) {
            throw new IllegalStateException("Explicit isolated investment_test_* MySQL database required");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }
    @Autowired ItemRepository items;
    @Autowired UserRepository users;
    @Autowired CsItemRepository catalogue;
    @Autowired MarketPriceSnapshotRepository snapshots;
    @Autowired MarketPriceWriteLockRepository guards;
    @Autowired MarketPricePersistenceService market;
    @Autowired TrackedItemService tracked;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired com.kalin.investment_tracker.validation.ResourceLimitsProperties limits;
    @MockitoBean PriceFeedService prices;
    @MockitoBean ActivityEventService activity;
    @MockitoBean TaskExecutor applicationTaskExecutor;

    @BeforeEach void resetObservations() { snapshots.deleteAll(); }

    @Test void thousandTrackedRowsUseABoundedPageAndNoCatalogueNPlusOne() throws Exception {
        User owner = user(); CsItem entry = entry();
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var insert = connection.prepareStatement("INSERT INTO item(user_id,item_name,market_hash_name,steam_found_check,quantity,price,show_in_dashboard,cs_item_id) VALUES(?,?,?,1,1,1,?,?)")) {
            for (int i = 0; i < 1000; i++) {
                insert.setInt(1, owner.getId()); insert.setString(2, "Load fixture " + i);
                insert.setString(3, entry.getMarketHashName() + "-" + i); insert.setBoolean(4, i % 2 == 0);
                insert.setString(5, entry.getId()); insert.addBatch();
            }
            insert.executeBatch();
        }
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true); statistics.clear();
        long started = System.nanoTime();
        try {
            var page = tracked.getTrackedItems(owner, false, 0, 20);
            assertEquals(1000, page.getTotalElements()); assertEquals(20, page.getContent().size());
            assertTrue(page.getContent().stream().allMatch(item -> entry.getId().equals(item.getCsItemId())));
            long statements = statistics.getPrepareStatementCount();
            assertTrue(statements <= 5, "Page mapping performed per-item queries: " + statements);
            System.out.printf("Stage 6 MySQL fixture: 1000 owned rows, 20 returned, %d SQL statements, %.2f ms%n", statements, (System.nanoTime() - started) / 1000000.0);
            var next = tracked.getTrackedItems(owner, false, 1, 20);
            assertTrue(page.getContent().stream().noneMatch(first -> next.getContent().stream().anyMatch(second -> first.getId().equals(second.getId()))));
            var pinned = tracked.getTrackedItems(owner, true, 0, 10);
            assertEquals(500, pinned.getTotalElements());
            assertTrue(pinned.getContent().stream().allMatch(item -> Boolean.TRUE.equals(item.getShowInDashboard())));
        } finally { statistics.setStatisticsEnabled(enabled); }
    }

    @Test void concurrentDifferentItemsCannotExceedTheAccountQuota() throws Exception {
        User owner = user(); CsItem firstEntry = entry(); CsItem secondEntry = entry();
        int previousLimit = limits.getTrackedItemsPerUser(); limits.setTrackedItemsPerUser(1);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<CsItem, Callable<Boolean>> task = candidate -> () -> {
                await(start);
                try { tracked.addTrackedItem(request(candidate), owner); return true; }
                catch (com.kalin.investment_tracker.validation.ResourceLimitException quota) {
                    assertEquals(409, quota.getStatus().value()); return false;
                }
            };
            var first = workers.submit(task.apply(firstEntry)); var second = workers.submit(task.apply(secondEntry)); start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { limits.setTrackedItemsPerUser(previousLimit); }
        assertEquals(1, items.countTrackedItemsByUserId(owner.getId()));
        verify(activity).recordUserAction(any(), eq("TRACKED_ITEM_ADDED"), anyString(), isNull());
    }

    @Test void v1UpgradePreservesRowsAndUniquenessAppliesOnlyToTrackedItems() throws Exception {
        String url = v1Database();
        try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO _user(id,account_locked,enabled,created_date) VALUES (51,0,1,NOW(6)),(52,0,1,NOW(6))");
            sql.executeUpdate("INSERT INTO item(user_id,market_hash_name,quantity,steam_found_check) VALUES"
                    + "(51,'Case',1,0),(51,'Case',1,0),(51,'Case',1,NULL),(51,'Case',1,1)");
            assertEquals(2, DatabaseMigrationTool.configuredFlyway(url, USER, PASSWORD).migrate().migrationsExecuted);
            assertThrows(java.sql.SQLException.class, () -> sql.executeUpdate(
                    "INSERT INTO item(user_id,market_hash_name,quantity,steam_found_check) VALUES(51,'case',1,1)"));
            sql.executeUpdate("INSERT INTO item(user_id,market_hash_name,quantity,steam_found_check) VALUES(52,'Case',1,1),(51,'Case',1,0)");
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM item")) { rows.next(); assertEquals(6, rows.getLong(1)); }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM item WHERE tracked_market_hash_name IS NULL")) {
                rows.next(); assertEquals(4, rows.getLong(1));
            }
        }
    }

    @Test void duplicateLegacyTrackingRowsRefuseUpgradeWithoutDeletingOrPartiallyChangingItems() throws Exception {
        String url = v1Database();
        try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO _user(id,account_locked,enabled,created_date) VALUES (51,0,1,NOW(6))");
            sql.executeUpdate("INSERT INTO item(user_id,market_hash_name,quantity,steam_found_check) VALUES(51,'Case',1,1),(51,'Case',1,1)");
            assertThrows(org.flywaydb.core.api.FlywayException.class,
                    () -> DatabaseMigrationTool.configuredFlyway(url, USER, PASSWORD).migrate());
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM item")) { rows.next(); assertEquals(2, rows.getLong(1)); }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='item' AND COLUMN_NAME='tracked_market_hash_name'")) {
                rows.next(); assertEquals(0, rows.getLong(1), "The single ALTER must be atomic on MySQL");
            }
        }
    }

    @Test void v3AddsNullableMetadataWithoutInventingDatesOrChangingLegacyValues() throws Exception {
        String url = v1Database();
        org.flywaydb.core.Flyway.configure().configuration(DatabaseMigrationTool.configuredFlyway(url, USER, PASSWORD).getConfiguration())
                .target("2").load().migrate();
        try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO market_price_snapshot(market_hash_name,source,source_version,observed_at,observation_date,price_usd,usd_to_eur_rate,price_eur,created_at) "
                    + "VALUES('Case','CSGOTRADER_24H','old','2026-10-20 08:00:00','2026-10-20',10,0.92,9.20,'2026-10-20 08:01:00')");
            assertEquals(1, DatabaseMigrationTool.configuredFlyway(url, USER, PASSWORD).migrate().migrationsExecuted);
            try (var rows = sql.executeQuery("SELECT price_eur,observed_at,source_published_at,fx_rate_as_of FROM market_price_snapshot")) {
                assertTrue(rows.next()); assertEquals(new BigDecimal("9.20"), rows.getBigDecimal(1));
                assertEquals(LocalDateTime.of(2026, 10, 20, 8, 0), rows.getObject(2, LocalDateTime.class));
                assertNull(rows.getTimestamp(3)); assertNull(rows.getTimestamp(4)); assertFalse(rows.next());
            }
        }
    }

    @Test void concurrentAddsAreSerializedAndCreateOnlyOneRecordAndOneSharedDailyPoint() throws Exception {
        User owner = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry.getMarketHashName(), "concurrent"));
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> add = () -> {
                await(start);
                try { tracked.addTrackedItem(request(entry), owner); return true; }
                catch (DuplicateTrackedItemException expected) { return false; }
            };
            var first = workers.submit(add); var second = workers.submit(add); start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, items.countTrackedItemsByUserId(owner.getId()));
        assertEquals(1, history(entry.getMarketHashName()));
        verify(activity).recordUserAction(any(), eq("TRACKED_ITEM_ADDED"), anyString(), isNull());
    }

    @Test void auditFailureRollsBackTheItemAndFirstObservationOnRealMysql() {
        User owner = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry.getMarketHashName(), "must-rollback"));
        doThrow(new IllegalStateException("audit failure")).when(activity).recordUserAction(any(), anyString(), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> tracked.addTrackedItem(request(entry), owner));
        assertEquals(0, items.countTrackedItemsByUserId(owner.getId()));
        assertEquals(0, history(entry.getMarketHashName()));
    }

    @Test void mutationReloadsAnAlreadyManagedItemAfterPriceChangedUnderAnOldReadSnapshot() throws Exception {
        User owner = user(); Item item = item(owner);
        CountDownLatch read = new CountDownLatch(1), changed = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var toggle = worker.submit(() -> tx().executeWithoutResult(status -> {
                assertEquals(new BigDecimal("1.00"), items.findById(item.getId()).orElseThrow().getPrice());
                read.countDown(); await(changed);
                tracked.toggleBookmark(item.getId(), owner);
                assertEquals(new BigDecimal("9.20"), entityManager.find(Item.class, item.getId()).getPrice());
            }));
            try { await(read); market.persistFeed(feed(item.getMarketHashName(), "new-price")); }
            finally { changed.countDown(); }
            toggle.get(15, TimeUnit.SECONDS);
        }
        Item stored = items.findById(item.getId()).orElseThrow();
        assertTrue(stored.getShowInDashboard()); assertEquals(new BigDecimal("9.20"), stored.getPrice());
        assertEquals(1, history(item.getMarketHashName()));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void priceAndSettingsUpdatesPreserveEachOtherInBothLockOrderings(boolean priceFirst) throws Exception {
        User owner = user(); Item item = item(owner);
        Runnable changePrice = () -> {
            guards.lockSource(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H).orElseThrow();
            items.updateTrackedPrice(item.getMarketHashName(), new BigDecimal("9.20"));
        };
        Runnable changeSettings = () -> tracked.updateTrackedItem(item.getId(),
                new TrackedItemUpdateRequest(true, new BigDecimal("12.00")), owner);
        race(priceFirst ? changePrice : changeSettings, priceFirst ? changeSettings : changePrice);
        Item stored = items.findById(item.getId()).orElseThrow();
        assertEquals(new BigDecimal("9.20"), stored.getPrice());
        assertEquals(new BigDecimal("12.00"), stored.getPriceNotification());
        assertTrue(stored.getShowInDashboard());
        verify(prices, never()).refreshPriceCacheIfNeeded();
        verify(prices, never()).getItemPriceData(any());
    }

    @Test void overlappingTogglesDoNotLoseTheSecondChange() throws Exception {
        User owner = user(); Item item = item(owner);
        race(() -> tracked.toggleBookmark(item.getId(), owner), () -> tracked.toggleBookmark(item.getId(), owner));
        assertFalse(items.findById(item.getId()).orElseThrow().getShowInDashboard());
    }

    @Test void invalidHistoryWriteRollsBackCurrentPricesOnRealMysql() {
        User owner = user(); Item item = item(owner);
        assertThrows(RuntimeException.class, () -> market.persistFeed(feed(item.getMarketHashName(), "x".repeat(129))));
        assertEquals(new BigDecimal("1.00"), items.findById(item.getId()).orElseThrow().getPrice());
        assertEquals(0, history(item.getMarketHashName()));
    }

    private void race(Runnable first, Runnable second) throws Exception {
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> tx().executeWithoutResult(status -> { first.run(); locked.countDown(); await(release); }));
            try {
                await(locked);
                var b = workers.submit(() -> { started.countDown(); tx().executeWithoutResult(status -> second.run()); });
                try { await(started); assertThrows(TimeoutException.class, () -> b.get(250, TimeUnit.MILLISECONDS)); }
                finally { release.countDown(); }
                b.get(15, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            a.get(15, TimeUnit.SECONDS);
        }
    }
    private String v1Database() throws Exception {
        String database = "investment_test_v2_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        String url = URL.substring(0, URL.lastIndexOf('/') + 1) + database;
        DatabaseMigrationTool.configuredV1Reference(url, USER, PASSWORD).migrate();
        return url;
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrency coordination timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private User user() {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.invalid"); user.setPassword("not-used");
        user.setCreatedDate(LocalDateTime.now()); user.setRoles(new ArrayList<>()); return users.saveAndFlush(user);
    }
    private CsItem entry() {
        String id = UUID.randomUUID().toString();
        return catalogue.saveAndFlush(CsItem.builder().id(id).name(id).marketHashName(id).build());
    }
    private Item item(User owner) {
        Item item = new Item(); item.setUser(owner); item.setSteamFoundCheck(true); item.setQuantity(1);
        item.setMarketHashName(UUID.randomUUID().toString()); item.setPrice(BigDecimal.ONE);
        return items.saveAndFlush(item);
    }
    private TrackedItemCreateRequest request(CsItem entry) { return new TrackedItemCreateRequest(null, null, entry.getId(), 1, false, null); }
    private PriceFeedSnapshot feed(String name, String version) {
        return new PriceFeedSnapshot(Map.of(name, new BigDecimal("10")), new BigDecimal("0.92"), version, OBSERVED, null, -1);
    }
    private int history(String name) {
        return snapshots.findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                name, MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, Instant.EPOCH).size();
    }
}
