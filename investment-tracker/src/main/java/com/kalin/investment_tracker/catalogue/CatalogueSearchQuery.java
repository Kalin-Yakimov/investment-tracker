package com.kalin.investment_tracker.catalogue;

import com.kalin.investment_tracker.validation.ApiRequestLimits;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** One search policy for cache keys, word matching and database ranking. */
public record CatalogueSearchQuery(String phrase, List<String> tokens) {
    static final String SEPARATOR_PATTERN = "[^\\p{L}\\p{N}]+";
    static final String DASH_PATTERN = "[-\u2010-\u2015\u2212]+";
    static final int MAXIMUM_TERMS = 32;
    private static final Pattern SEPARATORS = Pattern.compile(SEPARATOR_PATTERN);
    private static final Pattern DASHES = Pattern.compile(DASH_PATTERN);

    public CatalogueSearchQuery {
        tokens = List.copyOf(tokens);
    }

    public static CatalogueSearchQuery parse(String query) {
        if (query != null && query.length() > ApiRequestLimits.ITEM_TEXT_LENGTH) {
            throw new IllegalArgumentException("Search text is too long");
        }
        String phrase = normalize(query);
        List<String> tokens = phrase.isEmpty() ? List.of()
                : Arrays.stream(phrase.split(" ")).distinct().toList();
        if (tokens.size() > MAXIMUM_TERMS) {
            throw new IllegalArgumentException("Use at most " + MAXIMUM_TERMS + " search words");
        }
        return new CatalogueSearchQuery(phrase, tokens);
    }

    public static String normalize(String query) {
        if (query == null) return "";
        // Hyphenated identifiers are also searchable without their hyphen:
        // AK-47/AK47, M4A1-S/M4A1S, Five-SeveN/FiveSeven, Field-Tested/FieldTested.
        String compact = DASHES.matcher(query.toLowerCase(Locale.ROOT)).replaceAll("");
        return SEPARATORS.matcher(compact).replaceAll(" ").trim();
    }
}
