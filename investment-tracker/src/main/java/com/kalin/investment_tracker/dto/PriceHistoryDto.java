package com.kalin.investment_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryDto {
    private Long id;
    private Long itemId;
    private String itemName;
    private BigDecimal price;
    private int quantity;
    private BigDecimal totalPrice;
    private LocalDateTime dateAssigned;
    private LocalDateTime dateExtracted;
}
