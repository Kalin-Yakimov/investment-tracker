package com.kalin.investment_tracker.portfolio;

import com.kalin.investment_tracker.item.ItemStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PortfolioItemResponseDto {
    private Long id;
    private String itemName;
    private String csItemId;
    private ItemStatus itemStatus;
    private LocalDate date;
    private int quantity;
    private BigDecimal price;
    private String image; // The new field for the image URL
}
