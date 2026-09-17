package com.kalin.investment_tracker.dto;

import com.kalin.investment_tracker.model.CsItem;

public record CsItemDto(
        String id,
        String name,
        String marketHashName,
        String image
) {
    public static CsItemDto from(CsItem item) {
        return new CsItemDto(item.getId(), item.getName(), item.getMarketHashName(), item.getImage());
    }
}
