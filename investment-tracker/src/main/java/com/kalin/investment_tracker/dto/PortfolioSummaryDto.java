package com.kalin.investment_tracker.dto;

import java.math.BigDecimal;

public record PortfolioSummaryDto(long count, BigDecimal totalInvestmentValue) {
}
