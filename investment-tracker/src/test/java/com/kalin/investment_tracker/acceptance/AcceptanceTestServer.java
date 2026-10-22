package com.kalin.investment_tracker.acceptance;

import com.kalin.investment_tracker.InvestmentTrackerApplication;
import com.kalin.investment_tracker.catalogue.*;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.inventory.*;
import com.kalin.investment_tracker.item.*;
import com.kalin.investment_tracker.pricing.*;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.user.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Not a JUnit test or production source. Fixed loopback ports, fresh in-memory DB on every run. */
public class AcceptanceTestServer {
    public static final String PASSWORD = "acceptance-test-password";
    public static final String TEST_KEY = "synthetic-acceptance-only";
    private static final AtomicBoolean READY = new AtomicBoolean();
    private static final Map<String, String> MAILBOX = new ConcurrentHashMap<>();
    private static final Map<String, Long> IDS = new ConcurrentHashMap<>();

    public static void main(String[] ignored) throws Exception {
        if (!"isolated-loopback".equals(System.getenv("ACCEPTANCE_TEST_MODE")))
            throw new IllegalStateException("Explicit isolated acceptance mode required");
        var feed = mock(PriceFeedService.class);
        var mail = mock(EmailService.class);
        var inventory = mock(SteamInventoryService.class);
        var catalogueSync = mock(CsItemService.class);
        doAnswer(call -> { MAILBOX.put(call.getArgument(0), call.getArgument(4)); return null; })
                .when(mail).sendEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        when(inventory.calculateInventory(anyString())).thenAnswer(call -> {
            if (!"76561198000000000".equals(call.getArgument(0))) throw new IllegalArgumentException("Use the synthetic acceptance SteamID");
            return SteamInventoryResponseDto.builder().steamId64("76561198000000000").profileName("Acceptance Inventory")
                    .totalValue(new BigDecimal("12.50")).totalItemCount(2).uniqueItemsCount(1).unpricedItemCount(0)
                    .items(List.of(SteamInventoryItemDto.builder().itemName("QA Inventory Case").marketHashName("QA Inventory Case")
                            .quantity(2).unitPrice(new BigDecimal("6.25")).totalPrice(new BigDecimal("12.50"))
                            .typeCategory("Cases").tradable(true).tradableQuantity(2).build()))
                    .categoryCounts(Map.of("Cases", 2)).categoryValues(Map.of("Cases", new BigDecimal("12.50"))).build();
        });
        var outbound = mock(RestTemplate.class, call -> { throw new AssertionError("Outbound HTTP is disabled in acceptance tests"); });
        String[] arguments = {
                "--spring.profiles.active=acceptance", "--server.address=127.0.0.1", "--server.port=18080",
                "--spring.datasource.url=jdbc:h2:mem:acceptance-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.flyway.enabled=false", "--spring.mail.username=mail@example.invalid", "--spring.mail.password=synthetic-only",
                "--security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "--security.jwt.issuer=acceptance-fixture",
                "--security.jwt.audience=acceptance-browser", "--security.auth.cookie-secure=false", "--security.auth.cookie-same-site=Strict",
                "--app.cors.allowed-origins=http://127.0.0.1:14173", "--app.frontend.base-url=http://127.0.0.1:14173",
                "--app.bootstrap-admin-email=", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--price.update.initial-delay-ms=86400000", "--price.history.prune-cron=-", "--security.auth.cleanup-cron=-",
                "--security.verification.cleanup-cron=-", "--security.verification.logins-per-email=100", "--security.verification.logins-per-ip=200"
        };
        var context = new SpringApplicationBuilder(InvestmentTrackerApplication.class, FixtureConfiguration.class)
                .initializers(ctx -> ctx.addBeanFactoryPostProcessor(factory -> {
                    var registry = (BeanDefinitionRegistry) factory;
                    Map.of("csItemService", catalogueSync, "emailService", mail, "priceFeedService", feed,
                            "steamInventoryService", inventory, "restTemplate", outbound).forEach((name, value) -> {
                                registry.removeBeanDefinition(name); factory.registerSingleton(name, value);
                            });
                })).run(arguments);
        try {
            var users = context.getBean(UserRepository.class);
            var roles = context.getBean(RoleRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            Map<String, User> accounts = new HashMap<>();
            for (String role : List.of("USER", "ADMIN", "SUPPORT", "OTHER")) {
                String name = role.toLowerCase(Locale.ROOT);
                accounts.put(role, users.saveAndFlush(User.builder().username("Acceptance " + role)
                        .email("acceptance." + name + "@example.invalid").password(encoder.encode(PASSWORD))
                        .enabled(true).accountLocked(false).items(new ArrayList<>())
                        .roles(new ArrayList<>(List.of(roles.findByName(role.equals("OTHER") ? "USER" : role).orElseThrow()))).build()));
            }
            var catalogue = context.getBean(CsItemRepository.class);
            when(catalogueSync.searchItems(anyString())).thenAnswer(call ->
                    catalogue.searchCatalogue(CatalogueSearchQuery.parse(call.getArgument(0)), 8));
            var items = context.getBean(ItemRepository.class);
            Map<String, BigDecimal> prices = new HashMap<>();
            for (int index = 1; index <= 25; index++) {
                String name = index == 23 ? "AK-47 | Redline (Field-Tested)" : index == 24 ? "AK-47 | Redline (Minimal Wear)"
                        : "QA Browser Case " + String.format("%02d", index);
                CsItem entry = catalogue.saveAndFlush(CsItem.builder().id("acceptance-" + index).name(name).marketHashName(name).build());
                prices.put(name, new BigDecimal("6.25").add(BigDecimal.valueOf(index)));
                if (index <= 21) {
                    Item item = new Item(); item.setUser(accounts.get("USER")); item.setItemName(name); item.setCatalogueItem(entry);
                    item.setMarketHashName(name); item.setSteamFoundCheck(true); item.setShowInDashboard(index <= 12); item.setQuantity(1);
                    items.saveAndFlush(item);
                }
            }
            Item owned = new Item(); owned.setUser(accounts.get("OTHER")); owned.setItemName("Private fixture record");
            owned.setQuantity(1); owned.setPrice(BigDecimal.TEN); owned.setItemStatus(ItemStatus.BOUGHT); owned.setDate(LocalDate.now());
            IDS.put("privatePortfolioId", items.saveAndFlush(owned).getId());
            var persistence = context.getBean(MarketPricePersistenceService.class);
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            for (int daysAgo : List.of(6, 2, 0)) {
                Instant date = now.minus(Duration.ofDays(daysAgo));
                Map<String, BigDecimal> publicationPrices = new HashMap<>();
                prices.forEach((name, price) -> publicationPrices.put(name, price.subtract(BigDecimal.valueOf(daysAgo).movePointLeft(2))));
                persistence.persistFeed(new PriceFeedSnapshot(publicationPrices, BigDecimal.ONE, "fixture-day-" + daysAgo,
                        date, null, date.toEpochMilli(), date));
            }
            var publication = new PriceFeedSnapshot(prices, BigDecimal.ONE, "fixture-day-0", now, null, now.toEpochMilli(), now);
            when(feed.getCachedPublication()).thenReturn(publication);
            when(feed.getCachedItemPriceData(anyString())).thenAnswer(call -> {
                BigDecimal price = publication.priceEur(call.getArgument(0));
                return price == null ? null : new PriceFeedService.ItemPriceData(price,
                        PriceFreshnessDto.describe(now, now, now, now, false, now, new PriceFeedProperties()));
            });
            when(feed.describeStoredPrice(any())).thenAnswer(call -> {
                var price = (MarketPricePersistenceService.RecordedMarketPrice) call.getArgument(0);
                return new PriceFeedService.ItemPriceData(price.price(), PriceFreshnessDto.describe(price.observedAt(),
                        price.sourcePublishedAt(), now, price.fxRateAsOf(), true, now, new PriceFeedProperties()));
            });
            READY.set(true);
            System.out.println("Acceptance fixture ready: isolated H2, loopback only, outbound providers disabled");
        } catch (Throwable failure) { context.close(); throw failure; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("acceptance")
    static class FixtureConfiguration {
        @Bean @Order(-20) SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__acceptance__/**").csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.requestMatchers(request -> "127.0.0.1".equals(request.getRemoteAddr())
                            && "GET".equals(request.getMethod()) && (request.getRequestURI().equals("/__acceptance__/ready")
                            || TEST_KEY.equals(request.getHeader("X-Acceptance-Key")))).permitAll().anyRequest().denyAll()).build();
        }
    }

    @RestController @Profile("acceptance")
    static class FixtureEndpoints {
        @GetMapping("/__acceptance__/ready") ResponseEntity<?> ready() {
            return ResponseEntity.status(READY.get() ? 200 : 503).body(Map.of("fixture", "isolated-loopback", "ready", READY.get()));
        }
        @GetMapping("/__acceptance__/mailbox") Map<String, String> mailbox(@RequestParam String email) {
            return Map.of("code", MAILBOX.getOrDefault(email, ""));
        }
        @GetMapping("/__acceptance__/meta") Map<String, Long> meta() { return Map.copyOf(IDS); }
    }
}
