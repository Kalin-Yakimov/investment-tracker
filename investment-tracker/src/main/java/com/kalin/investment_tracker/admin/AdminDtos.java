package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.model.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast()
            );
        }
    }

    public record Overview(
            long totalUsers,
            long enabledUsers,
            long pendingActivationUsers,
            long lockedUsers,
            long activeSessions,
            long portfolioRecords,
            long trackedItems,
            long catalogueItems,
            long priceSnapshots,
            Instant latestPriceSnapshot,
            long administrativeEventsLast24Hours,
            JobState catalogueSync,
            JobState priceUpdate
    ) {
    }

    public record UserSummary(
            Integer id,
            String username,
            String email,
            boolean enabled,
            boolean locked,
            List<String> roles,
            LocalDateTime createdDate,
            LocalDateTime lastModifiedDate,
            long portfolioItems,
            long trackedItems,
            long activeSessions
    ) {
    }

    public record UserDetails(
            UserSummary user,
            LocalDateTime lastSessionActivity
    ) {
    }

    public record PortfolioItem(
            Long id,
            String itemName,
            String csItemId,
            ItemStatus itemStatus,
            LocalDate date,
            int quantity,
            BigDecimal price,
            String image
    ) {
    }

    public record TrackedItem(
            Long id,
            String itemName,
            String marketHashName,
            String csItemId,
            int quantity,
            BigDecimal currentPrice,
            Boolean showInDashboard,
            BigDecimal priceNotification,
            String image
    ) {
    }

    public record Session(
            String id,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt,
            LocalDateTime expiresAt,
            boolean remembered,
            String userAgent,
            String ipAddress
    ) {
    }

    public record Activity(
            Long id,
            String category,
            String eventType,
            String actor,
            String target,
            String reason,
            String details,
            String ipAddress,
            LocalDateTime createdAt
    ) {
    }

    public record ReasonRequest(
            @NotBlank(message = "Reason is required")
            @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
            String reason
    ) {
    }

    public record SupportRoleRequest(
            boolean enabled,
            @NotBlank(message = "Reason is required")
            @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
            String reason
    ) {
    }

    public record LockRequest(
            boolean locked,
            @NotBlank(message = "Reason is required")
            @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
            String reason
    ) {
    }

    public record ActionResult(String message) {
    }

    public record JobState(
            String status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String message
    ) {
    }
}
