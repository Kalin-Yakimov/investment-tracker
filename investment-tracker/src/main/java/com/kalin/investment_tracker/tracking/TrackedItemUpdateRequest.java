package com.kalin.investment_tracker.tracking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record TrackedItemUpdateRequest(
        Boolean showInDashboard,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price notification cannot be negative")
        @Digits(integer = 16, fraction = 2, message = "Price notification must fit the stored amount and have at most 2 decimal places")
        BigDecimal priceNotification
) {
}
