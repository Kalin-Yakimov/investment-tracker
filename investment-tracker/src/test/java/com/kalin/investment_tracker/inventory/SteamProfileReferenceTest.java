package com.kalin.investment_tracker.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SteamProfileReferenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"76561198000000001", " 76561198000000001 ",
            "https://steamcommunity.com/profiles/76561198000000001/",
            "http://steamcommunity.com/profiles/76561198000000001?x=1#profile",
            "steamcommunity.com/profiles/76561198000000001/inventory/",
            "https://www.steamcommunity.com/profiles/76561198000000001"})
    void normalizesDirectProfileForms(String input) {
        assertEquals(new SteamProfileReference("76561198000000001", true), SteamProfileReference.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Example_123", "@Example_123", "https://steamcommunity.com/id/Example_123/",
            "www.steamcommunity.com/id/Example_123?x=1#profile"})
    void normalizesVanityForms(String input) {
        assertEquals(new SteamProfileReference("example_123", false), SteamProfileReference.parse(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https://evil.example/id/example", "https://steamcommunity.com.evil.example/id/example",
            "https://steamcommunity.com@evil.example/id/example", "https://user@steamcommunity.com/id/example",
            "https://steamcommunity.com:8443/id/example", "file:///id/example", "//steamcommunity.com/id/example",
            "https://steamcommunity.com/id/../example", "https://steamcommunity.com/id/example%2fother",
            "https://steamcommunity.com/profiles/not-an-id", "123", "76561197960265728", "76561202255233024",
            "example?xml=1", "example/other"})
    void rejectsInvalidReferences(String input) {
        assertThrows(IllegalArgumentException.class, () -> SteamProfileReference.parse(input));
    }
    @Test void acceptsTheUnsignedAccountIdUpperBoundary() {
        assertTrue(SteamProfileReference.parse("76561202255233023").directId());
    }
    @Test void rejectsOversizedInput() {
        assertThrows(IllegalArgumentException.class, () -> SteamProfileReference.parse("a".repeat(501)));
    }
}
