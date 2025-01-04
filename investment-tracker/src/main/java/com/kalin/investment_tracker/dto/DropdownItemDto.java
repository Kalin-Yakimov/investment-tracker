package com.kalin.investment_tracker.dto;
import jakarta.validation.constraints.NotEmpty;

public record DropdownItemDto(

        Long id,

        @NotEmpty
        String itemName
) {
}
