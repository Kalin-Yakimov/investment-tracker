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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        fetchAndSaveItems();
        updateExistingItemsWithMarketHashNames();
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
                    String image = node.path("image").asText();
                    
                    // We only care about items that have a name and image, e.g., skins, stickers, agents
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

    /**
     * Converts CS API item name to Steam Community Market hash name format
     * This handles the conversion between different naming conventions
     */
    private String convertToMarketHashName(String csApiName) {
        // The CS API names should already match Steam market hash names for most items
        // But we can add specific conversions if needed
        
        // Example conversions (uncomment if needed):
        // if (csApiName.contains("★")) {
        //     return csApiName.replace("★", "★ ");
        // }
        
        // For now, return as-is since the ByMykel API uses Steam-compatible names
        return csApiName;
    }

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
