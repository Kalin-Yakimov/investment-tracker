package com.kalin.investment_tracker.pricing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MarketPriceWriteLockRepository extends JpaRepository<MarketPriceWriteLock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT guard FROM MarketPriceWriteLock guard WHERE guard.source = :source")
    Optional<MarketPriceWriteLock> lockSource(@Param("source") String source);
}
