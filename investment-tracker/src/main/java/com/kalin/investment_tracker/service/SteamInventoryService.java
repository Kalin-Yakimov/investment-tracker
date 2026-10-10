package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.dto.SteamInventoryResponseDto;
import com.kalin.investment_tracker.service.inventory.InventoryValuationService;
import com.kalin.investment_tracker.service.inventory.SteamCommunityClient;
import com.kalin.investment_tracker.service.inventory.SteamProfileReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SteamInventoryService {
    private final SteamCommunityClient steam;
    private final InventoryValuationService valuation;

    public SteamInventoryResponseDto calculateInventory(String inputUrlOrId) {
        SteamProfileReference profile = SteamProfileReference.parse(inputUrlOrId);
        String steamId = profile.directId() ? profile.identifier() : steam.resolveVanityId(profile.identifier());
        // Cache only after validation/resolution, so URL, vanity and direct-ID forms share a result.
        return valuation.calculate(steamId);
    }
}
