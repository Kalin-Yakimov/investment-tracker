package com.kalin.investment_tracker.catalogue;

import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual caching/proxies with mocked persistence/HTTP; no full application or database connection. */
@SpringJUnitConfig(CatalogueSearchCacheTest.Context.class)
class CatalogueSearchCacheTest {
    @TestConfiguration(proxyBeanMethods = false)
    @Import(CacheConfig.class)
    static class Context {
        // The small Spring context needs Boot's duration conversion without Boot application startup.
        @Bean static ConversionService conversionService() { return ApplicationConversionService.getSharedInstance(); }
        @Bean CsItemRepository repository() { return mock(CsItemRepository.class); }
        @Bean RestTemplate http() { return mock(RestTemplate.class); }
        @Bean CataloguePersistenceService persistence(CsItemRepository repository) {
            return new CataloguePersistenceService(repository);
        }
        @Bean CsItemService service(CsItemRepository repository, RestTemplate http, CataloguePersistenceService persistence) {
            return new CsItemService(repository, http, new ObjectMapper(), persistence);
        }
    }

    @Autowired CsItemService service;
    @Autowired CsItemRepository repository;
    @Autowired CataloguePersistenceService persistence;
    @Autowired CacheManager caches;
    @Autowired RestTemplate http;

    @BeforeEach
    void clear() {
        reset(repository, http);
        Objects.requireNonNull(caches.getCache(CacheConfig.CS_ITEMS_SEARCH_CACHE)).clear();
    }

    @Test
    void formattingVariantsReuseOneBoundedDatabaseSearch() {
        CsItem item = item("AK-47 | Redline");
        when(repository.searchCatalogue(any(), eq(8))).thenReturn(List.of(item));
        assertEquals(List.of(item), service.searchItems("AK-47 | Redline"));
        assertEquals(List.of(item), service.searchItems("  ak47   REDLINE  "));
        verify(repository).searchCatalogue(CatalogueSearchQuery.parse("ak47 redline"), 8);
        verifyNoInteractions(http);
    }

    @Test
    void catalogueSynchronizationInvalidatesPreviouslyRankedSuggestions() {
        CsItem previous = item("Previous Case");
        CsItem refreshed = item("Updated Case");
        when(repository.searchCatalogue(any(), eq(8))).thenReturn(List.of(previous), List.of(refreshed));
        assertEquals(List.of(previous), service.searchItems("case"));
        assertEquals(List.of(previous), service.searchItems("CASE"));
        persistence.saveCatalogue(List.of(refreshed), false);
        assertEquals(List.of(refreshed), service.searchItems("case"));
        verify(repository, times(2)).searchCatalogue(any(), eq(8));
        verify(repository).saveAllAndFlush(List.of(refreshed));
        verifyNoInteractions(http);
    }

    @Test
    void emptyPunctuationAndInvalidQueriesNeverReachTheDatabaseOrProvider() {
        for (String query : new String[]{null, "", " ", "%_", "|()"}) {
            assertTrue(service.searchItems(query).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> service.searchItems("a".repeat(256)));
        verifyNoInteractions(repository, http);
    }

    @Test
    void aCacheHitCannotBypassSearchLengthValidation() {
        when(repository.searchCatalogue(any(), eq(8))).thenReturn(List.of(item("Revolution Case")));
        service.searchItems("case");
        assertThrows(IllegalArgumentException.class, () -> service.searchItems("case" + " ".repeat(252)));
        verify(repository).searchCatalogue(CatalogueSearchQuery.parse("case"), 8);
        verifyNoInteractions(http);
    }

    private CsItem item(String name) {
        return CsItem.builder().id(name).name(name).marketHashName(name).image("image").build();
    }
}
