package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.CsItemSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsItemService {

    private final CsItemRepository csItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("Starting CS items database synchronization...");
        cleanExcludedItemsFromDatabase();

        long count = csItemRepository.count();
        if (count == 0) {
            log.info("Database is empty. Performing initial full CS items sync...");
            fetchAndSaveItems();
        } else {
            log.info("Database contains {} CS items. Checking for new missing items...", count);
            syncMissingItems();
        }
        updateExistingItemsWithMarketHashNames();
    }

    private boolean isExcludedCategory(String id, String name) {
        if (id == null || id.isEmpty()) return true;
        String lowerId = id.toLowerCase();
        String lowerName = name != null ? name.toLowerCase() : "";
        return lowerId.startsWith("graffiti-") ||
               lowerId.startsWith("key-") ||
               lowerId.startsWith("collectible-") ||
               lowerId.startsWith("collection-") ||
               lowerName.contains("sticker collection") ||
               lowerName.contains("patch collection") ||
               lowerName.contains("pin collection");
    }

    private void cleanExcludedItemsFromDatabase() {
        try {
            long count = csItemRepository.countExcludedCategories();
            if (count > 0) {
                log.info("Found {} excluded items in database (graffiti, keys, collectibles, collections). Cleaning up...", count);
                int deleted = csItemRepository.deleteExcludedCategories();
                log.info("Successfully purged {} excluded items from database", deleted);
            } else {
                log.info("No excluded items found in database");
            }
        } catch (Exception e) {
            log.error("Error purging excluded items from database", e);
        }
    }

    /**
     * Updates existing CS items that don't have marketHashName set
     * This is a one-time migration for existing data
     */
    private void updateExistingItemsWithMarketHashNames() {
        try {
            List<CsItem> itemsWithoutMarketHash = csItemRepository.findAll().stream()
                    .filter(item -> item.getMarketHashName() == null || item.getMarketHashName().isEmpty())
                    .toList();

            if (!itemsWithoutMarketHash.isEmpty()) {
                log.info("Found {} CS items without marketHashName, updating them...", itemsWithoutMarketHash.size());
                
                for (CsItem item : itemsWithoutMarketHash) {
                    item.setMarketHashName(convertToMarketHashName(item.getName()));
                }
                
                csItemRepository.saveAll(itemsWithoutMarketHash);
                log.info("Successfully updated {} CS items with marketHashName", itemsWithoutMarketHash.size());
            } else {
                log.info("All CS items already have marketHashName set");
            }
        } catch (Exception e) {
            log.error("Error updating existing CS items with marketHashName: {}", e.getMessage());
        }
    }

    @org.springframework.cache.annotation.CacheEvict(
            value = com.kalin.investment_tracker.config.CacheConfig.CS_ITEMS_SEARCH_CACHE,
            allEntries = true
    )
    public void fetchAndSaveItems() {
        String url = "https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/all.json";
        try {
            log.info("Sending request to: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            log.info("Response received. Status code: {}", response.getStatusCode());

            String jsonBody = response.getBody();
            if (jsonBody != null) {
                log.info("Raw JSON response (first 500 chars): {}", jsonBody.substring(0, Math.min(jsonBody.length(), 500)));
                
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                log.info("Found {} items from API", rootNode.size());
                
                List<CsItem> csItems = new ArrayList<>();
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode node = entry.getValue();
                    
                    String id = node.path("id").asText();
                    String name = node.path("name").asText();
                    if (isExcludedCategory(id, name)) {
                        continue;
                    }

                    String image = node.path("image").asText();
                    
                    if (id != null && !id.isEmpty() && name != null && !name.isEmpty() && image != null && !image.isEmpty()) {
                        String marketHashName = convertToMarketHashName(name);
                        csItems.add(CsItem.builder()
                                .id(id)
                                .name(name)
                                .image(image)
                                .marketHashName(marketHashName)
                                .build());
                    }
                }
                
                log.info("Saving items to database...");
                csItemRepository.saveAll(csItems);
                log.info("Successfully saved {} items", csItems.size());
            } else {
                log.warn("Response body is null!");
            }
        } catch (Exception e) {
            log.error("Failed to fetch and save items", e);
            throw new RuntimeException("Failed to parse and save items", e);
        }
    }

    public void syncMissingItems() {
        String url = "https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/all.json";
        try {
            Set<String> existingIds = new HashSet<>(csItemRepository.findAllItemIds());
            log.info("Loaded {} existing CS item IDs from database", existingIds.size());

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            String jsonBody = response.getBody();
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                List<CsItem> newItemsToSave = new ArrayList<>();

                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode node = entry.getValue();

                    String id = node.path("id").asText();
                    String name = node.path("name").asText();
                    if (isExcludedCategory(id, name)) {
                        continue;
                    }

                    if (id != null && !id.isEmpty() && !existingIds.contains(id)) {
                        String image = node.path("image").asText();

                        if (name != null && !name.isEmpty() && image != null && !image.isEmpty()) {
                            newItemsToSave.add(CsItem.builder()
                                    .id(id)
                                    .name(name)
                                    .image(image)
                                    .marketHashName(convertToMarketHashName(name))
                                    .build());
                        }
                    }
                }

                if (!newItemsToSave.isEmpty()) {
                    log.info("Found {} NEW CS items from API. Saving to database...", newItemsToSave.size());
                    csItemRepository.saveAll(newItemsToSave);
                    log.info("Successfully saved {} new CS items", newItemsToSave.size());
                } else {
                    log.info("Database is fully up to date with API! No new CS items found.");
                }
            }
        } catch (Exception e) {
            log.error("Error performing incremental CS items sync", e);
        }
    }

    /**
     * Converts CS API item name to Steam Community Market hash name format
     * This handles the conversion between different naming conventions
     */
    private String convertToMarketHashName(String csApiName) {
        return csApiName;
    }

    @org.springframework.cache.annotation.Cacheable(value = com.kalin.investment_tracker.config.CacheConfig.CS_ITEMS_SEARCH_CACHE, key = "#query.toLowerCase().trim()")
    public List<CsItem> searchItems(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String cleanQuery = query.trim().toLowerCase();
        List<CsItem> candidates = csItemRepository.findAll(
                CsItemSpecification.containsTextInName(cleanQuery),
                PageRequest.of(0, 40)
        ).getContent();

        String[] queryWords = cleanQuery.split("\\s+");
        String firstWord = queryWords[0];

        return candidates.stream()
                .sorted((a, b) -> {
                    int scoreA = calculateRelevanceScore(a.getName().toLowerCase(), cleanQuery, queryWords, firstWord);
                    int scoreB = calculateRelevanceScore(b.getName().toLowerCase(), cleanQuery, queryWords, firstWord);
                    return Integer.compare(scoreB, scoreA);
                })
                .limit(8)
                .toList();
    }

    private int calculateRelevanceScore(String name, String fullQuery, String[] queryWords, String firstWord) {
        int score = 0;

        // 1. Name starts with full query
        if (name.startsWith(fullQuery)) {
            score += 300;
        }

        // 2. Name starts with first word (e.g. "ak" at start of "ak-47 | redline")
        if (name.startsWith(firstWord)) {
            score += 200;
        } else if (name.startsWith("stattrak™ " + firstWord) || name.startsWith("★ " + firstWord)) {
            score += 150;
        }

        // 3. Contains full exact query string
        if (name.contains(fullQuery)) {
            score += 100;
        }

        // 4. Matches all query tokens in order
        boolean allWordsMatch = true;
        int lastIdx = -1;
        for (String word : queryWords) {
            int idx = name.indexOf(word);
            if (idx == -1 || idx < lastIdx) {
                allWordsMatch = false;
                break;
            }
            lastIdx = idx;
        }
        if (allWordsMatch) {
            score += 80;
        }

        // 5. Prefer shorter overall item names to avoid verbose sticker descriptions taking priority over main skins
        score -= name.length();

        return score;
    }
}
