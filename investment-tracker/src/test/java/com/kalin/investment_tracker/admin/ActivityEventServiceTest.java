package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityEventServiceTest {

    private final ActivityEventRepository repository = mock(ActivityEventRepository.class);
    private final ActivityEventService service = new ActivityEventService(repository);

    @Test
    void operationalAuditIsPublishedOnlyAfterCommitWithoutFreeTextOrIp() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ActivityEventService.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start(); logger.addAppender(capture);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.recordSecurityEvent(null, "TEST_SECURITY", "private-free-text", "198.51.100.42");
            assertEquals(0, capture.list.size());
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(c -> c.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            assertEquals(0, capture.list.size());
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            service.recordSecurityEvent(null, "TEST_SECURITY", "private-free-text", "198.51.100.42");
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            assertEquals(1, capture.list.size());
            String message = capture.list.getFirst().getFormattedMessage();
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("TEST_SECURITY"));
            org.junit.jupiter.api.Assertions.assertFalse(message.contains("private-free-text") || message.contains("198.51.100.42"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(capture); capture.stop();
        }
    }

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

    @Test
    void repeatedSupportAccessWithTheSameReasonIsGrouped() {
        User actor = new User();
        actor.setId(1);
        User target = new User();
        target.setId(2);
        when(repository.existsByActorUserIdAndTargetUserIdAndCategoryAndReasonAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(ActivityEvent.SUPPORT_ACCESS),
                org.mockito.ArgumentMatchers.eq("Opening the same support case"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(true);

        service.recordSupportAccess(
                actor,
                target,
                "SUPPORT_VIEW_OPENED",
                "Opening the same support case",
                "Opened the read-only support view",
                "127.0.0.1"
        );

        verify(repository, never()).save(any(ActivityEvent.class));
    }
}
