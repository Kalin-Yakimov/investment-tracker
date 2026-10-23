package com.kalin.investment_tracker.catalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueSearchQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"AK-47 | Redline", "ak47 redline", "  AK47   REDLINE  ", "AK–47 | Redline"})
    void formattingVariantsShareTheSameCacheKey(String text) {
        assertEquals("ak47 redline", CatalogueSearchQuery.normalize(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "|()", "%_", "★™"})
    void missingOrPunctuationOnlyQueriesCannotMatchTheEntireCatalogue(String text) {
        assertTrue(CatalogueSearchQuery.parse(text).tokens().isEmpty());
    }

    @Test
    void usesUnicodeWordsAndDeduplicatesTermsWithoutUsingSqlWildcards() {
        var search = CatalogueSearchQuery.parse("Sticker | Café (Holo) sticker_%");
        assertEquals(List.of("sticker", "café", "holo"), search.tokens());
        assertThrows(UnsupportedOperationException.class, () -> search.tokens().add("extra"));
    }

    @Test
    void normalizationDoesNotDependOnTheWindowsLanguage() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("music kit", CatalogueSearchQuery.normalize("MUSIC KIT"));
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void overlyLargeQueriesAreRejectedBeforeBuildingSql() {
        assertThrows(IllegalArgumentException.class, () -> CatalogueSearchQuery.parse("a".repeat(256)));
        String terms = IntStream.range(0, 33).mapToObj(index -> "w" + index).collect(java.util.stream.Collectors.joining(" "));
        assertThrows(IllegalArgumentException.class, () -> CatalogueSearchQuery.parse(terms));
    }
}
