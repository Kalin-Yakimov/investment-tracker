package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record TrackedItemCreateRequest(
        String itemName,
        String marketHashName,
        String csItemId,
        @Min(value = 0, message = "Quantity cannot be negative") int quantity,
        Boolean showInDashboard,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price notification cannot be negative")
        BigDecimal priceNotification
) {
}
