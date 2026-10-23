package com.kalin.investment_tracker.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SteamInventoryRequest(
        @NotBlank(message = "Steam profile URL or SteamID is required")
        @Size(max = 500, message = "Steam profile URL is too long") String steamUrl
) {
}
