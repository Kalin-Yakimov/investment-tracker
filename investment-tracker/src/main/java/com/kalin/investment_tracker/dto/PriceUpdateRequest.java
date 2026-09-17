package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PriceUpdateRequest(
        @NotEmpty(message = "No item IDs provided") List<Long> itemIds
) {
}
