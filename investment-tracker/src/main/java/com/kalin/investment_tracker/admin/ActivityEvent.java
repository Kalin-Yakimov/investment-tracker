package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "activity_event",
        indexes = {
                @Index(name = "idx_activity_target_created", columnList = "target_user_id,created_at"),
                @Index(name = "idx_activity_actor_created", columnList = "actor_user_id,created_at"),
                @Index(name = "idx_activity_category_created", columnList = "category,created_at")
        }
)
public class ActivityEvent {

    public static final String USER_ACTION = "USER_ACTION";
    public static final String SUPPORT_ACCESS = "SUPPORT_ACCESS";
    public static final String ADMIN_ACTION = "ADMIN_ACTION";
    public static final String SECURITY = "SECURITY";
    public static final String SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(length = 500)
    private String reason;

    @Column(length = 1000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
