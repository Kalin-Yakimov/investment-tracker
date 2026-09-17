package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.CacheConfig;
import com.kalin.investment_tracker.dto.SteamInventoryItemDto;
import com.kalin.investment_tracker.dto.SteamInventoryResponseDto;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.exeption.PrivateSteamInventoryException;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.repository.CsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamInventoryService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CsItemRepository csItemRepository;
    private final SteamPriceService steamPriceService;

    @Cacheable(value = CacheConfig.INVENTORY_CALCULATOR_CACHE, key = "#inputUrlOrId.trim().toLowerCase()")
    public SteamInventoryResponseDto calculateInventory(String inputUrlOrId) {
        if (inputUrlOrId == null || inputUrlOrId.trim().isEmpty()) {
            throw new IllegalArgumentException("Steam profile URL or ID cannot be empty");
        }

        String cleanedInput = inputUrlOrId.trim();
        String steamId64 = resolveSteamId64(cleanedInput);
        
        if (steamId64 == null || steamId64.isEmpty()) {
            throw new IllegalArgumentException("Could not resolve a valid SteamID64 from input: " + cleanedInput);
        }

        // Fetch profile info (Name & Avatar) via Steam XML endpoint
        ProfileInfo profileInfo = fetchProfileInfo(steamId64);

        // Fetch inventory from Steam Community API with browser User-Agent
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        String inventoryUrl = String.format("https://steamcommunity.com/inventory/%s/730/2?l=english&count=2000", steamId64);
        String jsonBody;
        try {
            ResponseEntity<String> response = restTemplate.exchange(inventoryUrl, HttpMethod.GET, entity, String.class);
            jsonBody = response.getBody();
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            throw new PrivateSteamInventoryException("Your Steam inventory is set to Private. Please go to Steam Profile -> Edit Profile -> Privacy Settings and set 'Inventory' to Public.");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ExternalServiceException("Steam API rate limit reached. Please wait a few minutes and try again.");
        } catch (Exception e) {
            log.error("Failed to fetch Steam inventory for {}: {}", steamId64, e.getMessage());
            throw new ExternalServiceException("Failed to reach Steam servers. Make sure the profile URL is valid and inventory is public.");
        }

        if (jsonBody == null || jsonBody.isEmpty()) {
            throw new ExternalServiceException("Empty response received from Steam. Make sure your inventory is set to Public.");
        }

        return parseAndCalculateInventory(steamId64, profileInfo, jsonBody);
    }

    private String resolveSteamId64(String input) {
        // Direct SteamID64 (17 digits starting with 7656)
        if (input.matches("^7656119\\d{10}$")) {
            return input;
        }

        // URL parsing: extract profiles/7656119... or id/custom_name
        String vanityOrId = null;
        if (input.contains("/profiles/")) {
            String[] parts = input.split("/profiles/");
            if (parts.length > 1) {
                vanityOrId = parts[1].split("/")[0].trim();
                if (vanityOrId.matches("^7656119\\d{10}$")) {
                    return vanityOrId;
                }
            }
        } else if (input.contains("/id/")) {
            String[] parts = input.split("/id/");
            if (parts.length > 1) {
                vanityOrId = parts[1].split("/")[0].trim();
            }
        } else {
            // Strip any leading @ or slashes
            vanityOrId = input.replaceAll("^[/@]+", "").trim();
        }

        if (vanityOrId == null || vanityOrId.isEmpty()) {
            return null;
        }

        // If vanityOrId is 17 digits, return directly
        if (vanityOrId.matches("^7656119\\d{10}$")) {
            return vanityOrId;
        }

        // Resolve custom vanity name via Steam Profile XML page
        try {
            String xmlUrl = String.format("https://steamcommunity.com/id/%s/?xml=1", vanityOrId);
            String xmlResponse = restTemplate.getForObject(xmlUrl, String.class);
            if (xmlResponse != null && xmlResponse.contains("<steamID64>")) {
                int start = xmlResponse.indexOf("<steamID64>") + 11;
                int end = xmlResponse.indexOf("</steamID64>");
                if (start > 10 && end > start) {
                    return xmlResponse.substring(start, end).trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve vanity name '{}' via XML: {}", vanityOrId, e.getMessage());
        }

        return null;
    }

    private ProfileInfo fetchProfileInfo(String steamId64) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            String xmlUrl = String.format("https://steamcommunity.com/profiles/%s/?xml=1", steamId64);
            ResponseEntity<String> response = restTemplate.exchange(xmlUrl, HttpMethod.GET, entity, String.class);
            String xmlResponse = response.getBody();

            if (xmlResponse != null) {
                String name = null;
                String avatar = null;
                if (xmlResponse.contains("<steamID>")) {
                    int s = xmlResponse.indexOf("<steamID>") + 9;
                    int e = xmlResponse.indexOf("</steamID>");
                    if (s > 8 && e > s) name = xmlResponse.substring(s, e).replace("<![CDATA[", "").replace("]]>", "").trim();
                }
                if (xmlResponse.contains("<avatarFull>")) {
                    int s = xmlResponse.indexOf("<avatarFull>") + 12;
                    int e = xmlResponse.indexOf("</avatarFull>");
                    if (s > 11 && e > s) avatar = xmlResponse.substring(s, e).replace("<![CDATA[", "").replace("]]>", "").trim();
                }
                return new ProfileInfo(name != null ? name : "Steam User (" + steamId64 + ")", avatar);
            }
        } catch (Exception e) {
            log.warn("Could not fetch profile info for {}: {}", steamId64, e.getMessage());
        }
        return new ProfileInfo("Steam User (" + steamId64 + ")", null);
    }

    private SteamInventoryResponseDto parseAndCalculateInventory(String steamId64, ProfileInfo profileInfo, String jsonBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode assetsNode = rootNode.path("assets");
            JsonNode descriptionsNode = rootNode.path("descriptions");

            if (assetsNode.isMissingNode() || assetsNode.isEmpty() || !assetsNode.isArray()) {
                return SteamInventoryResponseDto.builder()
                        .steamId64(steamId64)
                        .profileName(profileInfo.name)
                        .avatarUrl(profileInfo.avatarUrl)
                        .totalValue(BigDecimal.ZERO)
                        .totalItemCount(0)
                        .uniqueItemsCount(0)
                        .items(List.of())
                        .categoryCounts(Map.of())
                        .categoryValues(Map.of())
                        .build();
            }

            // Build map of classId_instanceId -> Description details
            Map<String, DescriptionItem> descriptionMap = new HashMap<>();
            if (descriptionsNode.isArray()) {
                for (JsonNode desc : descriptionsNode) {
                    String classId = desc.path("classid").asText();
                    String instanceId = desc.path("instanceid").asText("0");
                    String key = classId + "_" + instanceId;

                    String marketHashName = desc.path("market_hash_name").asText();
                    if (marketHashName.isEmpty()) {
                        marketHashName = desc.path("name").asText();
                    }

                    String iconUrl = desc.path("icon_url").asText();
                    boolean tradable = desc.path("tradable").asInt(1) == 1;

                    descriptionMap.put(key, new DescriptionItem(marketHashName, iconUrl, tradable));
                }
            }

            // Count total instances per marketHashName
            Map<String, Integer> itemQuantities = new HashMap<>();
            Map<String, String> itemIcons = new HashMap<>();

            for (JsonNode asset : assetsNode) {
                String classId = asset.path("classid").asText();
                String instanceId = asset.path("instanceid").asText("0");
                String key = classId + "_" + instanceId;

                DescriptionItem desc = descriptionMap.get(key);
                if (desc != null && desc.marketHashName != null && !desc.marketHashName.isEmpty()) {
                    itemQuantities.put(desc.marketHashName, itemQuantities.getOrDefault(desc.marketHashName, 0) + 1);
                    if (desc.iconUrl != null && !desc.iconUrl.isEmpty()) {
                        itemIcons.putIfAbsent(desc.marketHashName, desc.iconUrl);
                    }
                }
            }

            // Pre-fetch images from cs_items database for all item names
            List<CsItem> csItemsList = itemQuantities.isEmpty()
                    ? List.of()
                    : csItemRepository.findCatalogueEntriesForMarketNames(itemQuantities.keySet());
            Map<String, String> dbImageMap = new HashMap<>();
            Map<String, String> csItemIdMap = new HashMap<>();
            for (CsItem c : csItemsList) {
                if (c.getImage() != null && !c.getImage().isEmpty()) {
                    dbImageMap.putIfAbsent(c.getMarketHashName(), c.getImage());
                    dbImageMap.putIfAbsent(c.getName(), c.getImage());
                }
                csItemIdMap.putIfAbsent(c.getMarketHashName(), c.getId());
            }

            List<SteamInventoryItemDto> itemDtos = new ArrayList<>();
            BigDecimal grandTotalValue = BigDecimal.ZERO;
            int grandTotalCount = 0;

            Map<String, Integer> categoryCounts = new HashMap<>();
            Map<String, BigDecimal> categoryValues = new HashMap<>();

            SteamInventoryItemDto topItem = null;
            BigDecimal highestUnitPrice = BigDecimal.ZERO;

            for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
                String marketHashName = entry.getKey();
                int qty = entry.getValue();

                // Determine Image: DB Image first, fallback to Steam icon_url
                String image = dbImageMap.get(marketHashName);
                if (image == null || image.isEmpty()) {
                    String steamIcon = itemIcons.get(marketHashName);
                    if (steamIcon != null && !steamIcon.isEmpty()) {
                        image = "https://community.akamai.steamstatic.com/economy/image/" + steamIcon;
                    }
                }

                // Determine Unit Price
                BigDecimal unitPrice = BigDecimal.ZERO;
                SteamPriceService.ItemPriceData priceData = steamPriceService.getItemPriceData(marketHashName);
                if (priceData != null && priceData.price24h != null) {
                    unitPrice = priceData.price24h;
                } else {
                    Optional<BigDecimal> fetchedPrice = steamPriceService.tryFetchPrice(marketHashName);
                    if (fetchedPrice.isPresent()) {
                        unitPrice = fetchedPrice.get();
                    }
                }

                BigDecimal itemTotalValue = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                grandTotalValue = grandTotalValue.add(itemTotalValue);
                grandTotalCount += qty;

                String category = categorizeItem(marketHashName);
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + qty);
                categoryValues.put(category, categoryValues.getOrDefault(category, BigDecimal.ZERO).add(itemTotalValue));

                SteamInventoryItemDto dto = SteamInventoryItemDto.builder()
                        .itemName(marketHashName)
                        .marketHashName(marketHashName)
                        .csItemId(csItemIdMap.get(marketHashName))
                        .image(image)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(itemTotalValue)
                        .tradable(true)
                        .typeCategory(category)
                        .build();

                itemDtos.add(dto);

                if (unitPrice.compareTo(highestUnitPrice) > 0) {
                    highestUnitPrice = unitPrice;
                    topItem = dto;
                }
            }

            // Sort items by total price descending
            itemDtos.sort((a, b) -> b.getTotalPrice().compareTo(a.getTotalPrice()));

            return SteamInventoryResponseDto.builder()
                    .steamId64(steamId64)
                    .profileName(profileInfo.name)
                    .avatarUrl(profileInfo.avatarUrl)
                    .totalValue(grandTotalValue.setScale(2, RoundingMode.HALF_UP))
                    .totalItemCount(grandTotalCount)
                    .uniqueItemsCount(itemDtos.size())
                    .items(itemDtos)
                    .categoryCounts(categoryCounts)
                    .categoryValues(categoryValues)
                    .mostValuableItem(topItem)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing inventory JSON for {}: {}", steamId64, e.getMessage());
            throw new ExternalServiceException("Failed to process inventory data from Steam.");
        }
    }

    private String categorizeItem(String name) {
        if (name == null) return "Other";
        String lower = name.toLowerCase();

        if (lower.contains("★") || lower.contains("knife") || lower.contains("bayonet") || lower.contains("karambit") || lower.contains("gloves") || lower.contains("wraps")) {
            return "Knives & Gloves";
        }
        if (lower.contains("sticker") || lower.contains("patch") || lower.contains("graffiti")) {
            return "Stickers & Patches";
        }
        if (lower.contains("capsule")) {
            return "Capsules";
        }
        if (lower.contains("souvenir")) {
            return "Souvenirs";
        }
        if (lower.contains("case") || lower.contains("container") || lower.contains("package")) {
            return "Cases";
        }
        if (lower.contains("|")) {
            return "Weapon Skins";
        }
        return "Other";
    }

    private record ProfileInfo(String name, String avatarUrl) {}
    private record DescriptionItem(String marketHashName, String iconUrl, boolean tradable) {}
}
