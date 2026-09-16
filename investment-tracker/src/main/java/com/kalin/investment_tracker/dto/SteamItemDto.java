package com.kalin.investment_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SteamItemDto {
    private Long id;
    private String itemName;
    private String marketHashName;
    private String csItemId;
    private int quantity;
    private BigDecimal price;
    private BigDecimal boughtPrice;
    private LocalDate boughtDate;
    private Boolean showInDashboard;
    private BigDecimal priceNotification;
    private Boolean steamFoundCheck;
    private String image;
    private BigDecimal price24h;
}
