package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.SteamInventoryResponseDto;
import com.kalin.investment_tracker.service.SteamInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin("http://localhost:3000")
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
public class SteamInventoryController {

    private final SteamInventoryService steamInventoryService;

    @PostMapping("/calculate")
    public ResponseEntity<?> calculateInventory(@RequestBody Map<String, String> request) {
        String steamUrl = request.get("steamUrl");
        if (steamUrl == null || steamUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Steam profile URL or SteamID is required"));
        }

        try {
            SteamInventoryResponseDto result = steamInventoryService.calculateInventory(steamUrl);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("PRIVACY_PRIVATE:")) {
                return ResponseEntity.badRequest().body(Map.of("error", msg.replace("PRIVACY_PRIVATE:", "").trim(), "code", "PRIVACY_PRIVATE"));
            }
            return ResponseEntity.status(500).body(Map.of("error", msg != null ? msg : "An unexpected error occurred while fetching inventory"));
        } catch (Exception e) {
            log.error("Unhandled error calculating inventory for {}: {}", steamUrl, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process inventory request. Please check the profile URL and try again."));
        }
    }
}
