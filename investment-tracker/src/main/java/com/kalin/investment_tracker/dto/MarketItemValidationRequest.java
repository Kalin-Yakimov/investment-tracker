package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.kalin.investment_tracker.validation.ApiRequestLimits;

public record MarketItemValidationRequest(
        @NotBlank(message = "marketHashName is required")
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Market name is too long") String marketHashName
) {
}
