package com.kalin.investment_tracker.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class SteamInventoryController {

    private final SteamInventoryService steamInventoryService;

    @PostMapping("/calculate")
    public ResponseEntity<SteamInventoryResponseDto> calculateInventory(
            @RequestBody @Valid SteamInventoryRequest request
    ) {
        return ResponseEntity.ok(steamInventoryService.calculateInventory(request.steamUrl()));
    }
}
