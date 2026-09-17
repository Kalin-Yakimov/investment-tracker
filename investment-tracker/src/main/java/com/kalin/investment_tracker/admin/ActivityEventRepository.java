package com.kalin.investment_tracker.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    Page<ActivityEvent> findByTargetUserIdOrderByCreatedAtDesc(Integer targetUserId, Pageable pageable);

    Page<ActivityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCategoryInAndCreatedAtAfter(Collection<String> categories, LocalDateTime cutoff);
}
