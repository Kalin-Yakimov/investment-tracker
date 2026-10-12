package com.kalin.investment_tracker.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface MarketPriceSnapshotRepository extends JpaRepository<MarketPriceSnapshot, Long> {

    Optional<MarketPriceSnapshot> findFirstBySourceAndSourceVersionOrderByIdAsc(String source, String sourceVersion);

    Optional<MarketPriceSnapshot> findFirstBySourceOrderByObservedAtDescIdDesc(String source);

    List<MarketPriceSnapshot> findBySourceAndObservationDateAndMarketHashNameIn(
            String source, LocalDate observationDate, Collection<String> names);

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

    @Query("SELECT MAX(snapshot.observedAt) FROM MarketPriceSnapshot snapshot WHERE snapshot.source = :source")
    Instant findLatestObservedAtBySource(@Param("source") String source);
}
