package com.kalin.investment_tracker.catalogue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/** MySQL 8/H2-compatible, bounded query. Rank before LIMIT, never after a candidate cutoff. */
@RequiredArgsConstructor
public class CsItemSearchRepositoryImpl implements CsItemSearchRepository {
    private static final List<String> NAME_COLUMNS = List.of("name_words", "name_compact");
    private static final List<String> MARKET_COLUMNS = List.of("market_words", "market_compact");
    private static final List<String> FULL_COLUMNS = List.of("name_words", "name_compact", "market_words", "market_compact");
    private static final List<String> BODY_COLUMNS = List.of("name_body", "compact_body", "market_body", "compact_market_body");
    private static final List<String> RANK_COLUMNS = List.of("name_words", "name_compact", "market_words", "market_compact",
            "name_body", "compact_body", "market_body", "compact_market_body");
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<CsItem> searchCatalogue(CatalogueSearchQuery search, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Search result limit must be between 1 and 100");
        if (search.tokens().isEmpty()) return List.of();

        String sql = """
                WITH normalized AS (
                    SELECT c.id, c.name, c.image, c.market_hash_name,
                           TRIM(REGEXP_REPLACE(LOWER(c.name), :separators, ' ')) AS name_words,
                           TRIM(REGEXP_REPLACE(REGEXP_REPLACE(LOWER(c.name), :dashes, ''), :separators, ' ')) AS name_compact,
                           TRIM(REGEXP_REPLACE(LOWER(COALESCE(c.market_hash_name, '')), :separators, ' ')) AS market_words,
                           TRIM(REGEXP_REPLACE(REGEXP_REPLACE(LOWER(COALESCE(c.market_hash_name, '')), :dashes, ''), :separators, ' ')) AS market_compact
                    FROM cs_items c
                    WHERE c.name IS NOT NULL AND TRIM(c.name) <> ''
                      AND NOT (LOWER(c.id) LIKE 'graffiti-%%' OR LOWER(c.id) LIKE 'key-%%'
                           OR LOWER(c.id) LIKE 'collectible-%%' OR LOWER(c.id) LIKE 'collection-%%'
                           OR LOWER(c.name) LIKE '%%sticker collection%%'
                           OR LOWER(c.name) LIKE '%%patch collection%%' OR LOWER(c.name) LIKE '%%pin collection%%')
                ), searchable AS (
                    SELECT n.*,
                           REGEXP_REPLACE(name_words, :modifier, '') AS name_body,
                           REGEXP_REPLACE(name_compact, :modifier, '') AS compact_body,
                           REGEXP_REPLACE(market_words, :modifier, '') AS market_body,
                           REGEXP_REPLACE(market_compact, :modifier, '') AS compact_market_body
                    FROM normalized n
                )
                SELECT id, name, image, market_hash_name FROM searchable
                WHERE (%s OR %s)
                ORDER BY CASE
                    WHEN %s THEN 0
                    WHEN %s THEN 1
                    WHEN %s THEN 2
                    WHEN %s THEN 3
                    WHEN %s THEN 4
                    ELSE 5 END ASC,
                    (%s) DESC,
                    CASE WHEN name_words LIKE 'stattrak %%' OR name_words LIKE 'souvenir %%' THEN 1 ELSE 0 END ASC,
                    CHAR_LENGTH(name) ASC, LOWER(name) ASC, id ASC
                """.formatted(
                allWords(NAME_COLUMNS, search.tokens().size()), allWords(MARKET_COLUMNS, search.tokens().size()),
                anyComparison(FULL_COLUMNS, "= :phrase"), anyComparison(BODY_COLUMNS, "= :phrase"),
                anyComparison(RANK_COLUMNS, "LIKE :phrasePrefix"), anyComparison(RANK_COLUMNS, "LIKE :firstPrefix"),
                anyWordLike(FULL_COLUMNS, "phraseContains", false), exactWordScore(search.tokens().size()));

        Query query = entityManager.createNativeQuery(sql, CsItem.class)
                .setParameter("separators", CatalogueSearchQuery.SEPARATOR_PATTERN)
                .setParameter("dashes", CatalogueSearchQuery.DASH_PATTERN)
                .setParameter("modifier", "^(stattrak|souvenir) +")
                .setParameter("phrase", search.phrase())
                .setParameter("phrasePrefix", search.phrase() + "%")
                .setParameter("firstPrefix", search.tokens().getFirst() + "%")
                .setParameter("phraseContains", "% " + search.phrase() + "%")
                .setMaxResults(limit);
        for (int index = 0; index < search.tokens().size(); index++) {
            // Tokens contain only letters/numbers: user '%'/'_' never become SQL wildcards.
            String token = search.tokens().get(index);
            query.setParameter("prefix" + index, "% " + token + "%");
            query.setParameter("whole" + index, "% " + token + " %");
        }
        List<?> rows = query.getResultList();
        return rows.stream().map(CsItem.class::cast).toList();
    }

    private static String allWords(List<String> columns, int size) {
        return "(" + IntStream.range(0, size).mapToObj(index -> anyWordLike(columns, "prefix" + index, false))
                .collect(java.util.stream.Collectors.joining(" AND ")) + ")";
    }

    private static String exactWordScore(int size) {
        return IntStream.range(0, size).mapToObj(index ->
                        "CASE WHEN " + anyWordLike(FULL_COLUMNS, "whole" + index, true) + " THEN 1 ELSE 0 END")
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    private static String anyComparison(List<String> columns, String comparison) {
        return "(" + columns.stream().map(column -> column + " " + comparison)
                .collect(java.util.stream.Collectors.joining(" OR ")) + ")";
    }

    private static String anyWordLike(List<String> columns, String parameter, boolean completeWord) {
        return "(" + columns.stream().map(column -> "CONCAT(' ', " + column
                        + (completeWord ? ", ' ')" : ")") + " LIKE :" + parameter)
                .collect(java.util.stream.Collectors.joining(" OR ")) + ")";
    }
}
