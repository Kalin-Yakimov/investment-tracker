package com.kalin.investment_tracker.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsItemSynchronizationTest {
    private final CsItemRepository repository = mock(CsItemRepository.class);
    private final RestTemplate http = mock(RestTemplate.class);
    private final CataloguePersistenceService persistence = mock(CataloguePersistenceService.class);
    private final CsItemService service = new CsItemService(repository, http, new ObjectMapper(), persistence);

    @Test void emptyMalformedAndUnusableResponsesNeverEraseStoredCatalogue() {
        for (String body : new String[]{"{}", "[]", "null", "not json", "{\"item\":{\"id\":\"a\"}}"}) {
            reply(body);
            assertThrows(ExternalServiceException.class, service::fetchAndSaveItems);
        }
        verifyNoInteractions(persistence);
    }
    @Test void arraysAndObjectsBothMapCatalogueEntriesAndExcludedCategoriesAreSkipped() {
        String item = "{\"id\":\"skin-1\",\"name\":\"AK-47 | Redline (Field-Tested)\",\"image\":\"image\"}";
        for (String json : List.of("[" + item + "]", "{\"first\":" + item + "}")) {
            reply(json); service.fetchAndSaveItems();
        }
        verify(persistence, times(2)).saveCatalogue(argThat(list -> list.size() == 1
                && list.getFirst().getName().equals(list.getFirst().getMarketHashName())), eq(false));
        reply("[" + item + ",{\"id\":\"key-1\",\"name\":\"Key\",\"image\":\"image\"}]");
        service.syncMissingItems();
        verify(persistence).saveCatalogue(argThat(list -> list.size() == 1), eq(true));
    }
    @Test void startupContinuesWithStoredCatalogueWhenExternalServiceIsOffline() {
        when(repository.count()).thenReturn(10L); when(repository.findAll()).thenReturn(List.of());
        when(http.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(String.class))).thenThrow(new IllegalStateException());
        assertDoesNotThrow(service::init);
        verifyNoInteractions(persistence);
        assertThrows(ExternalServiceException.class, service::syncMissingItems);
    }
    @Test void nullBodyIsFailureNotSuccessfulSynchronization() {
        when(http.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(String.class))).thenReturn(ResponseEntity.ok().build());
        assertThrows(ExternalServiceException.class, service::fetchAndSaveItems);
        verifyNoInteractions(persistence);
    }
    private void reply(String body) {
        when(http.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(String.class))).thenReturn(ResponseEntity.ok(body));
    }
}
