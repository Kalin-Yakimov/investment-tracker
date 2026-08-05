package com.kalin.investment_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SteamInventoryItemDto {
    private String itemName;
    private String marketHashName;
    private String image;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String csItemId;
    private boolean tradable;
    private String typeCategory; // Skins, Cases, Stickers, Capsules, Souvenirs, Other
}
