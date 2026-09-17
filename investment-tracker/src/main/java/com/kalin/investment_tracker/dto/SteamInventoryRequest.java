package com.kalin.investment_tracker.dto;

import jakarta.validation.constraints.NotBlank;

public record SteamInventoryRequest(
        @NotBlank(message = "Steam profile URL or SteamID is required") String steamUrl
) {
}
