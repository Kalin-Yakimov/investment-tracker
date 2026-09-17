package com.kalin.investment_tracker.admin;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityEventServiceTest {

    private final ActivityEventRepository repository = mock(ActivityEventRepository.class);
    private final ActivityEventService service = new ActivityEventService(repository);

    @Test
    void supportReasonMustBeMeaningful() {
        assertThrows(IllegalArgumentException.class, () -> service.requireReason("help"));
    }

    @Test
    void supportReasonIsTrimmed() {
        assertEquals("Investigating missing price data", service.requireReason("  Investigating missing price data  "));
    }

    @Test
    void oldestEventsAreDeletedWhenRetentionLimitIsExceeded() {
        ActivityEvent oldestEvent = ActivityEvent.builder()
                .category(ActivityEvent.SYSTEM)
                .eventType("OLD_EVENT")
                .build();
        when(repository.count()).thenReturn(101L);
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(oldestEvent)));

        service.recordUserAction(null, "NEW_EVENT", "Recorded event", "127.0.0.1");

        verify(repository).deleteAllInBatch(List.of(oldestEvent));
    }
}
