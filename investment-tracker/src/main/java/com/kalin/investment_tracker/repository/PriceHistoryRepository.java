package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByItemIdOrderByDateExtractedDesc(Long itemId);

    List<PriceHistory> findByItemAndDateExtractedBetweenOrderByDateExtractedDesc(
            Item item,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    void deleteByItemId(Long itemId);
}
