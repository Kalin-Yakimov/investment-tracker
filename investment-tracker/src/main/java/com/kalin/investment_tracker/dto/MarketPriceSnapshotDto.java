package com.kalin.investment_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceSnapshotDto {
    private Long id;
    private BigDecimal price;
    private Instant dateExtracted;
    private String source;
}
