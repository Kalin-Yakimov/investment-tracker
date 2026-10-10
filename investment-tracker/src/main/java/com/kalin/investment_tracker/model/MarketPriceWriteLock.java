package com.kalin.investment_tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A database mutex shared by all application instances writing the same feed. */
@Entity
@Table(name = "market_price_write_lock")
public class MarketPriceWriteLock {
    @Id
    @Column(nullable = false, length = 50)
    private String source;

    protected MarketPriceWriteLock() { }

    public MarketPriceWriteLock(String source) {
        this.source = source;
    }
}
