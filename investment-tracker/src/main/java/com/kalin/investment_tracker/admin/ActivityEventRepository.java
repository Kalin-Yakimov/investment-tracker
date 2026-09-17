package com.kalin.investment_tracker.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    Page<ActivityEvent> findByTargetUserIdOrderByCreatedAtDesc(Integer targetUserId, Pageable pageable);

    Page<ActivityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ActivityEvent> findByCategoryInOrderByCreatedAtDesc(
            Collection<String> categories,
            Pageable pageable
    );

    List<ActivityEvent> findByCategoryInOrderByCreatedAtDesc(Collection<String> categories);

    boolean existsByActorUserIdAndTargetUserIdAndCategoryAndReasonAndCreatedAtAfter(
            Integer actorUserId,
            Integer targetUserId,
            String category,
            String reason,
            LocalDateTime cutoff
    );

    long countByCategoryInAndCreatedAtAfter(Collection<String> categories, LocalDateTime cutoff);
}
