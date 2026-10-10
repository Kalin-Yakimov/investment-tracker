package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.kalin.investment_tracker.validation.ApiRequestLimits;

import java.util.List;

public record PriceUpdateRequest(
        @NotEmpty(message = "No item IDs provided")
        @Size(max = ApiRequestLimits.PRICE_UPDATE_BATCH_SIZE, message = "Too many item IDs in one request")
        List<@NotNull(message = "Item IDs cannot be null") @Positive(message = "Item IDs must be positive") Long> itemIds
) {
}
