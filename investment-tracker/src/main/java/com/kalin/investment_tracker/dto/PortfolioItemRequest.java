package com.kalin.investment_tracker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kalin.investment_tracker.model.ItemStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioItemRequest(
        @NotBlank(message = "Item name is required") String itemName,
        String csItemId,
        @NotNull(message = "Item status is required") ItemStatus itemStatus,
        @NotNull(message = "Date is required") @JsonFormat(pattern = "dd.MM.yyyy") LocalDate date,
        @Min(value = 1, message = "Quantity must be at least 1") int quantity,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative") BigDecimal price
) {
}
