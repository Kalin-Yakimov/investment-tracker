package com.kalin.investment_tracker.portfolio;

import java.math.BigDecimal;

public record PortfolioSummaryDto(long count, BigDecimal totalInvestmentValue) {
}
