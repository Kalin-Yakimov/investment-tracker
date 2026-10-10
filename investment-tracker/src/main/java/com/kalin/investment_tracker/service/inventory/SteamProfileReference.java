package com.kalin.investment_tracker.service.inventory;

import java.net.URI;
import java.util.Locale;

public record SteamProfileReference(String identifier, boolean directId) {
    public static SteamProfileReference parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 500) throw invalid();
        String input = raw.trim();
        if (input.matches("[0-9]+")) return direct(input);
        if (input.toLowerCase(Locale.ROOT).startsWith("steamcommunity.com/")
                || input.toLowerCase(Locale.ROOT).startsWith("www.steamcommunity.com/")) input = "https://" + input;
        if (input.contains(":") || input.contains("/")) {
            try {
                URI uri = URI.create(input);
                String host = uri.getHost();
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || host == null || !(host.equalsIgnoreCase("steamcommunity.com") || host.equalsIgnoreCase("www.steamcommunity.com"))
                        || uri.getRawUserInfo() != null || uri.getPort() != -1) throw invalid();
                String path = uri.getRawPath();
                if (path == null || !path.matches("/(profiles|id)/[A-Za-z0-9_-]+/?(?:inventory/?)?")) throw invalid();
                String[] parts = path.split("/");
                return parts[1].equals("profiles") ? direct(parts[2]) : vanity(parts[2]);
            } catch (IllegalArgumentException failure) { throw invalid(); }
        }
        return vanity(input.startsWith("@") ? input.substring(1) : input);
    }

    public static boolean isValidId(String value) {
        if (value == null || !value.matches("[0-9]{17}")) return false;
        long id = Long.parseLong(value);
        // Public-universe individual account, desktop instance and a non-zero unsigned account ID.
        return id > 76561197960265728L && id <= 76561202255233023L;
    }

    private static SteamProfileReference direct(String value) {
        if (!isValidId(value)) throw invalid();
        return new SteamProfileReference(value, true);
    }

    private static SteamProfileReference vanity(String value) {
        if (!value.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return new SteamProfileReference(value.toLowerCase(Locale.ROOT), false);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter a valid Steam profile URL, SteamID64 or custom profile name");
    }
}
