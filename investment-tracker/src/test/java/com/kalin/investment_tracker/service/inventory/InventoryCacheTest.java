package com.kalin.investment_tracker.service.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.CacheConfig;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.service.SteamInventoryService;
import com.kalin.investment_tracker.service.SteamPriceService;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kalin.investment_tracker.service.inventory.SteamCommunityClientTest.*;

class InventoryCacheTest {
    private AnnotationConfigApplicationContext context;
    private SteamInventoryService service;
    private RestTemplate http;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Fixture.class);
        service = context.getBean(SteamInventoryService.class);
        http = context.getBean(RestTemplate.class);
        lenient().when(http.exchange(contains("?xml=1"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("<profile><steamID64>" + ID + "</steamID64><steamID>Example</steamID></profile>"));
    }
    @AfterEach void close() { context.close(); }
    private org.mockito.stubbing.OngoingStubbing<ResponseEntity<String>> inventoryCall() {
        return when(http.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)));
    }
    private ResponseEntity<String> inventory() { return ResponseEntity.ok(page(asset("1", "2"), DESCRIPTION, "")); }

    @Test void directIdUrlsAndVanityNamesShareOneCalculation() {
        inventoryCall().thenReturn(inventory());
        var first = service.calculateInventory("Example");
        assertSame(first, service.calculateInventory(ID));
        assertSame(first, service.calculateInventory("https://steamcommunity.com/profiles/" + ID + "/"));
        assertSame(first, service.calculateInventory("https://steamcommunity.com/id/EXAMPLE/"));
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(http, times(1)).exchange(eq("https://steamcommunity.com/id/example/?xml=1"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
    @Test void invalidInputIsRejectedBeforeCacheOrHttp() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateInventory(null));
        assertThrows(IllegalArgumentException.class, () -> service.calculateInventory("https://other.example/id/example"));
        verifyNoInteractions(http);
    }
    @Test void incompleteResultIsNotCachedAndASecondAttemptCanRecover() {
        inventoryCall().thenReturn(ResponseEntity.ok("{\"success\":0}"), inventory());
        assertThrows(ExternalServiceException.class, () -> service.calculateInventory(ID));
        assertEquals(2, service.calculateInventory(ID).getTotalItemCount());
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
    @Test void priceOutageIsNotCachedAsAnAllZeroResult() {
        inventoryCall().thenReturn(inventory());
        SteamPriceService prices = context.getBean(SteamPriceService.class);
        when(prices.getCurrentPrices(any())).thenThrow(new ExternalServiceException("No usable feed"))
                .thenReturn(Map.of("Revolution Case", new BigDecimal("2.00")));
        assertThrows(ExternalServiceException.class, () -> service.calculateInventory(ID));
        assertEquals(new BigDecimal("4.00"), service.calculateInventory(ID).getTotalValue());
        verify(http, times(2)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
    @Test void concurrentRequestsForOneProfileShareOneDownload() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        inventoryCall().thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test release timed out");
            return inventory();
        });
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.calculateInventory(ID));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> {
                secondStarted.countDown();
                return service.calculateInventory("https://steamcommunity.com/profiles/" + ID + "/");
            });
            try { assertTrue(secondStarted.await(5, TimeUnit.SECONDS)); }
            finally { release.countDown(); }
            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        verify(http, times(1)).exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Configuration
    @EnableCaching
    static class Fixture {
        @Bean RestTemplate http() { return mock(RestTemplate.class); }
        @Bean SteamPriceService prices() { return mock(SteamPriceService.class); }
        @Bean CsItemRepository catalogue() { return mock(CsItemRepository.class); }
        @Bean SteamInventoryProperties settings() {
            SteamInventoryProperties settings = new SteamInventoryProperties();
            settings.setPageDelayMs(0);
            return settings;
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean CacheManager cacheManager() {
            Duration ttl = Duration.ofMinutes(15);
            return new CacheConfig().cacheManager(ttl, ttl, ttl, 1, 10);
        }
        @Bean SteamCommunityClient steam(RestTemplate http, SteamInventoryProperties settings, Clock clock) {
            return new SteamCommunityClient(http, new ObjectMapper(), settings, clock);
        }
        @Bean InventoryValuationService valuation(SteamCommunityClient steam, CsItemRepository catalogue, SteamPriceService prices) {
            return new InventoryValuationService(steam, catalogue, prices);
        }
        @Bean SteamInventoryService facade(SteamCommunityClient steam, InventoryValuationService valuation) {
            return new SteamInventoryService(steam, valuation);
        }
    }
}
