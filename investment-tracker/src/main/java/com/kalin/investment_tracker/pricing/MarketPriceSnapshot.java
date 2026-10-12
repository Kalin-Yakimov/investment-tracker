package com.kalin.investment_tracker.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A price value that was actually published by an external market-data feed.
 *
 * Snapshots are shared by market hash name instead of being duplicated for
 * every user who tracks the same item.
 */
@Entity
@Table(
        name = "market_price_snapshot",
        indexes = {
                @Index(name = "idx_mps_market_observed", columnList = "market_hash_name,observed_at"),
                @Index(name = "idx_mps_observed_at", columnList = "observed_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mps_market_source_version",
                        columnNames = {"market_hash_name", "source", "source_version"}
                ),
                @UniqueConstraint(
                        name = "uk_mps_market_source_day",
                        columnNames = {"market_hash_name", "source", "observation_date"}
                )
        }
)
public class MarketPriceSnapshot {

    public static final String SOURCE_CSGOTRADER_24H = "CSGOTRADER_24H";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_hash_name", nullable = false, length = 500)
    private String marketHashName;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_version", nullable = false, length = 128)
    private String sourceVersion;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "observation_date", nullable = false)
    private LocalDate observationDate;

    @Column(name = "price_usd", nullable = false, precision = 18, scale = 4)
    private BigDecimal priceUsd;

    @Column(name = "usd_to_eur_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal usdToEurRate;

    @Column(name = "price_eur", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceEur;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MarketPriceSnapshot() {
    }

    public MarketPriceSnapshot(
            String marketHashName,
            String source,
            String sourceVersion,
            Instant observedAt,
            BigDecimal priceUsd,
            BigDecimal usdToEurRate,
            BigDecimal priceEur
    ) {
        this.marketHashName = marketHashName;
        this.source = source;
        this.sourceVersion = sourceVersion;
        setObservedAt(observedAt);
        this.priceUsd = priceUsd;
        this.usdToEurRate = usdToEurRate;
        this.priceEur = priceEur;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMarketHashName() {
        return marketHashName;
    }

    public void setMarketHashName(String marketHashName) {
        this.marketHashName = marketHashName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
        this.observationDate = observedAt == null ? null : observedAt.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    public LocalDate getObservationDate() {
        return observationDate;
    }

    @PrePersist
    @PreUpdate
    private void alignObservationDate() {
        setObservedAt(observedAt);
    }

    public BigDecimal getPriceUsd() {
        return priceUsd;
    }

    public void setPriceUsd(BigDecimal priceUsd) {
        this.priceUsd = priceUsd;
    }

    public BigDecimal getUsdToEurRate() {
        return usdToEurRate;
    }

    public void setUsdToEurRate(BigDecimal usdToEurRate) {
        this.usdToEurRate = usdToEurRate;
    }

    public BigDecimal getPriceEur() {
        return priceEur;
    }

    public void setPriceEur(BigDecimal priceEur) {
        this.priceEur = priceEur;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
