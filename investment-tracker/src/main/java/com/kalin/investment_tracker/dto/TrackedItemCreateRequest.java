package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kalin.investment_tracker.validation.ApiRequestLimits;

import java.math.BigDecimal;

public record TrackedItemCreateRequest(
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Item name is too long") String itemName,
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Market name is too long") String marketHashName,
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Catalogue ID is too long") String csItemId,
        @Min(value = 0, message = "Quantity cannot be negative") int quantity,
        Boolean showInDashboard,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price notification cannot be negative")
        @Digits(integer = 16, fraction = 2, message = "Price notification must fit the stored amount and have at most 2 decimal places")
        BigDecimal priceNotification
) {
    @JsonIgnore
    @AssertTrue(message = "A catalogue ID or market name is required")
    public boolean isItemReferencePresent() {
        return (csItemId != null && !csItemId.isBlank()) || (marketHashName != null && !marketHashName.isBlank());
    }
}
