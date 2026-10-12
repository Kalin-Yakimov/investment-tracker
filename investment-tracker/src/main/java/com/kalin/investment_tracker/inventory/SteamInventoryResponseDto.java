package com.kalin.investment_tracker.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SteamInventoryResponseDto {
    private String steamId64;
    private String profileName;
    private String avatarUrl;
    private BigDecimal totalValue;
    private int totalItemCount;
    private int uniqueItemsCount;
    private int unpricedItemCount;
    private List<SteamInventoryItemDto> items;
    private Map<String, Integer> categoryCounts;
    private Map<String, BigDecimal> categoryValues;
    private SteamInventoryItemDto mostValuableItem;
}
