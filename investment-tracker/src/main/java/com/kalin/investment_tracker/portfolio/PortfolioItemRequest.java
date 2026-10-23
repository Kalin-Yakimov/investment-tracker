package com.kalin.investment_tracker.portfolio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kalin.investment_tracker.item.ItemStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import com.kalin.investment_tracker.validation.ApiRequestLimits;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Item name is too long") String itemName,
        @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH, message = "Catalogue ID is too long") String csItemId,
        @NotNull(message = "Item status is required") ItemStatus itemStatus,
        @NotNull(message = "Date is required") @JsonFormat(pattern = "dd.MM.yyyy") LocalDate date,
        @Min(value = 1, message = "Quantity must be at least 1") int quantity,
        @NotNull(message = "Price is required")
        @Digits(integer = 36, fraction = 2, message = "Price must fit the stored amount and have at most 2 decimal places")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative") BigDecimal price
) {
}
