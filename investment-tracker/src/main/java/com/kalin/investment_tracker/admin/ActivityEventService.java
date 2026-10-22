package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ActivityEventService {

    private static final int MAX_STORED_EVENTS = 100;
    private static final int DELETE_BATCH_SIZE = 500;
    private static final int SUPPORT_ACCESS_GROUPING_MINUTES = 30;

    private final ActivityEventRepository repository;

    @Transactional
    public void recordUserAction(User user, String eventType, String details, String ipAddress) {
        saveAndApplyRetention(build(user, user, ActivityEvent.USER_ACTION, eventType, null, details, ipAddress));
    }

    @Transactional
    public void recordSecurityEvent(User user, String eventType, String details, String ipAddress) {
        saveAndApplyRetention(build(user, user, ActivityEvent.SECURITY, eventType, null, details, ipAddress));
    }

    @Transactional
    public void recordSupportAccess(
            User actor,
            User target,
            String eventType,
            String reason,
            String details,
            String ipAddress
    ) {
        String cleanReason = requireReason(reason);
        boolean recentMatchingAccess = repository
                .existsByActorUserIdAndTargetUserIdAndCategoryAndReasonAndCreatedAtAfter(
                        actor.getId(),
                        target.getId(),
                        ActivityEvent.SUPPORT_ACCESS,
                        cleanReason,
                        LocalDateTime.now().minusMinutes(SUPPORT_ACCESS_GROUPING_MINUTES)
                );
        if (recentMatchingAccess) {
            return;
        }
        saveAndApplyRetention(build(
                actor,
                target,
                ActivityEvent.SUPPORT_ACCESS,
                eventType,
                cleanReason,
                details,
                ipAddress
        ));
    }

    @Transactional
    public void recordAdminAction(
            User actor,
            User target,
            String eventType,
            String reason,
            String details,
            String ipAddress
    ) {
        saveAndApplyRetention(build(
                actor,
                target,
                ActivityEvent.ADMIN_ACTION,
                eventType,
                requireReason(reason),
                details,
                ipAddress
        ));
    }

    @Transactional
    public void recordSystemAction(User actor, String eventType, String reason, String details, String ipAddress) {
        saveAndApplyRetention(build(
                actor,
                null,
                ActivityEvent.SYSTEM,
                eventType,
                requireReason(reason),
                details,
                ipAddress
        ));
    }

    public String requireReason(String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < 5) {
            throw new IllegalArgumentException("A support reason of at least 5 characters is required");
        }
        return limit(cleanReason, 500);
    }

    private void saveAndApplyRetention(ActivityEvent event) {
        repository.save(event);
        // The UI keeps its 100-event ring. Important events also reach the protected
        // operational log, but only after commit and without free text, email or IP.
        if (!ActivityEvent.USER_ACTION.equals(event.getCategory())) {
            Runnable publish = () -> log.info("audit category={} action={} actorId={} targetId={}",
                    event.getCategory(), event.getEventType().matches("[A-Z0-9_]{1,80}") ? event.getEventType() : "OTHER",
                    event.getActorUser() == null ? null : event.getActorUser().getId(),
                    event.getTargetUser() == null ? null : event.getTargetUser().getId());
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void afterCommit() { publish.run(); }
                        });
            } else publish.run();
        }
        long excess = repository.count() - MAX_STORED_EVENTS;

        while (excess > 0) {
            int batchSize = (int) Math.min(excess, DELETE_BATCH_SIZE);
            List<ActivityEvent> oldestEvents = repository.findAll(PageRequest.of(
                    0,
                    batchSize,
                    Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))
            )).getContent();
            if (oldestEvents.isEmpty()) {
                return;
            }
            repository.deleteAllInBatch(oldestEvents);
            excess -= oldestEvents.size();
        }
    }

    private ActivityEvent build(
            User actor,
            User target,
            String category,
            String eventType,
            String reason,
            String details,
            String ipAddress
    ) {
        return ActivityEvent.builder()
                .actorUser(actor)
                .targetUser(target)
                .category(category)
                .eventType(limit(eventType, 80))
                .reason(reason)
                .details(limit(details, 1000))
                .ipAddress(limit(ipAddress, 64))
                .build();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
