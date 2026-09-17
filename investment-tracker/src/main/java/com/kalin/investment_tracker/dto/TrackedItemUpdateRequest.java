package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record TrackedItemUpdateRequest(
        Boolean showInDashboard,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price notification cannot be negative")
        BigDecimal priceNotification
) {
}
