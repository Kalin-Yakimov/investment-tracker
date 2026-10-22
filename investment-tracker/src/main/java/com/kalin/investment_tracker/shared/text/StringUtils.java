package com.kalin.investment_tracker.shared.text;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class StringUtils {
    public static String normalizeString(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        // Replace multiple spaces between words with a single one.
        String normalized = trimmed.replaceAll("\\s+", " ");

        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String formatString(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        String normalized = trimmed.replaceAll("\\s+", " ");

        // Splits the string into words, first letter of each word capitalize and join them back together.
        return Arrays.stream(normalized.split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
