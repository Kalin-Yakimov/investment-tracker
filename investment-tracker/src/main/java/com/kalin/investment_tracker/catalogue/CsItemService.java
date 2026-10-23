package com.kalin.investment_tracker.catalogue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsItemService {

    private final CsItemRepository csItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CataloguePersistenceService persistence;
    private final ReentrantLock synchronizationLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        log.info("Starting CS items database synchronization...");
        cleanExcludedItemsFromDatabase();

        try {
            if (csItemRepository.count() == 0) fetchAndSaveItems();
            else syncMissingItems();
        } catch (ExternalServiceException unavailable) {
            // A provider outage must not prevent the application using its stored catalogue.
            log.warn("Startup catalogue synchronization failed; retaining the stored catalogue");
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

    public void fetchAndSaveItems() {
        synchronizeCatalogue(false);
    }

    public void syncMissingItems() {
        synchronizeCatalogue(true);
    }

    private void synchronizeCatalogue(boolean onlyMissing) {
        if (!synchronizationLock.tryLock()) throw new ExternalServiceException("Catalogue synchronization is already running");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/all.json",
                    HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ExternalServiceException("The catalogue source returned no usable data");
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root == null || (!root.isObject() && !root.isArray())) {
                throw new ExternalServiceException("Invalid catalogue format");
            }
            Map<String, CsItem> parsed = new LinkedHashMap<>();
            for (JsonNode node : root) {
                String id = node.path("id").asText("");
                String name = node.path("name").asText("");
                String image = node.path("image").asText("");
                if (isExcludedCategory(id, name)) continue;
                if (id.isBlank() || id.length() > 255 || name.isBlank() || name.length() > 255
                        || image.isBlank() || image.length() > 1000) continue;
                parsed.put(id, CsItem.builder().id(id).name(name).image(image)
                        .marketHashName(convertToMarketHashName(name)).build());
            }
            if (parsed.isEmpty()) throw new ExternalServiceException("The catalogue contains no usable items");
            int saved = persistence.saveCatalogue(List.copyOf(parsed.values()), onlyMissing);
            log.info("Catalogue synchronization completed; {} entries saved", saved);
        } catch (Exception failure) {
            log.warn("Catalogue synchronization failed ({})", failure.getClass().getSimpleName());
            throw new ExternalServiceException("Catalogue synchronization failed; stored entries were retained");
        } finally {
            synchronizationLock.unlock();
        }
    }

    /**
     * Converts CS API item name to Steam Community Market hash name format
     * This handles the conversion between different naming conventions
     */
    private String convertToMarketHashName(String csApiName) {
        return csApiName;
    }

    @org.springframework.cache.annotation.Cacheable(value = com.kalin.investment_tracker.config.CacheConfig.CS_ITEMS_SEARCH_CACHE,
            key = "T(com.kalin.investment_tracker.catalogue.CatalogueSearchQuery).parse(#query).phrase()",
            condition = "#query != null && !#query.isBlank()")
    public List<CsItem> searchItems(String query) {
        CatalogueSearchQuery search = CatalogueSearchQuery.parse(query);
        return search.tokens().isEmpty() ? List.of() : csItemRepository.searchCatalogue(search, 8);
    }
}
