package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityEventService {

    private static final int MAX_STORED_EVENTS = 100;
    private static final int DELETE_BATCH_SIZE = 500;

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
        saveAndApplyRetention(build(
                actor,
                target,
                ActivityEvent.SUPPORT_ACCESS,
                eventType,
                requireReason(reason),
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
