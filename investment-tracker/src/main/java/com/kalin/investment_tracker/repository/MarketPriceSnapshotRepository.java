package com.kalin.investment_tracker.repository;

import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketPriceSnapshotRepository extends JpaRepository<MarketPriceSnapshot, Long> {

    boolean existsByMarketHashNameAndSourceAndSourceVersion(
            String marketHashName,
            String source,
            String sourceVersion
    );

    List<MarketPriceSnapshot> findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
            String marketHashName,
            String source,
            Instant cutoff
    );

    @Modifying
    @Query("DELETE FROM MarketPriceSnapshot snapshot WHERE snapshot.observedAt < :cutoff")
    int deleteByObservedAtBefore(@Param("cutoff") Instant cutoff);

    @Query("SELECT MAX(snapshot.observedAt) FROM MarketPriceSnapshot snapshot")
    Instant findLatestObservedAt();
}
