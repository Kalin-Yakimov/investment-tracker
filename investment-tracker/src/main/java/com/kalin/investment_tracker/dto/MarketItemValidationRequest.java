package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.NotBlank;

public record MarketItemValidationRequest(
        @NotBlank(message = "marketHashName is required") String marketHashName
) {
}
