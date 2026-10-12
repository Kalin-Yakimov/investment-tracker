package com.kalin.investment_tracker.inventory;

import com.kalin.investment_tracker.config.CacheConfig;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static com.kalin.investment_tracker.inventory.SteamCommunityClient.*;

@Service
@RequiredArgsConstructor
public class InventoryValuationService {
    private static final int CATALOGUE_BATCH_SIZE = 500;
    private final SteamCommunityClient steam;
    private final CsItemRepository catalogue;
    private final PriceFeedService prices;

    @Cacheable(value = CacheConfig.INVENTORY_CALCULATOR_CACHE, key = "#steamId", sync = true)
    public SteamInventoryResponseDto calculate(String steamId) {
        Inventory inventory = steam.loadInventory(steamId);
        Map<GroupKey, Group> groups = new LinkedHashMap<>();
        for (Asset asset : inventory.assets()) {
            Description description = inventory.descriptions().get(asset.description());
            // A display name is not a market identifier. Unmarketable items remain in the count.
            GroupKey key = new GroupKey(description.marketName(), description.marketName() == null ? asset.description() : null);
            Group group = groups.computeIfAbsent(key, ignored -> new Group(description));
            group.quantity = Math.addExact(group.quantity, asset.amount());
            if (description.tradable()) group.tradableQuantity = Math.addExact(group.tradableQuantity, asset.amount());
        }
        List<String> names = groups.values().stream().map(group -> group.description.marketName())
                .filter(Objects::nonNull).distinct().sorted().toList();
        Map<String, BigDecimal> unitPrices = names.isEmpty() ? Map.of() : prices.getCurrentPrices(names);
        Map<String, CsItem> catalogueItems = findCatalogueItems(names);
        List<SteamInventoryItemDto> items = new ArrayList<>();
        Map<String, Integer> categoryCounts = new TreeMap<>();
        Map<String, BigDecimal> categoryValues = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int totalQuantity = 0;
        int unpriced = 0;
        for (Group group : groups.values()) {
            Description description = group.description;
            String name = description.marketName() == null ? description.name() : description.marketName();
            BigDecimal unitPrice = description.marketName() == null ? null : unitPrices.get(description.marketName());
            BigDecimal itemValue = unitPrice == null ? null
                    : unitPrice.multiply(BigDecimal.valueOf(group.quantity)).setScale(2, RoundingMode.HALF_UP);
            String category = categorize(name);
            totalQuantity = Math.addExact(totalQuantity, group.quantity);
            if (unitPrice == null) unpriced = Math.addExact(unpriced, group.quantity);
            else total = total.add(itemValue);
            categoryCounts.merge(category, group.quantity, Math::addExact);
            categoryValues.merge(category, itemValue == null ? BigDecimal.ZERO : itemValue, BigDecimal::add);
            CsItem entry = catalogueItems.get(description.marketName());
            String image = entry == null ? null : entry.getImage();
            if (image == null || image.isBlank()) image = steamImage(description.icon());
            items.add(SteamInventoryItemDto.builder()
                    .itemName(name).marketHashName(description.marketName()).csItemId(entry == null ? null : entry.getId())
                    .image(image).quantity(group.quantity).unitPrice(unitPrice).totalPrice(itemValue)
                    .tradable(group.tradableQuantity == group.quantity).tradableQuantity(group.tradableQuantity)
                    .nonTradableQuantity(group.quantity - group.tradableQuantity).typeCategory(category).build());
        }
        Comparator<SteamInventoryItemDto> byName = Comparator.comparing(SteamInventoryItemDto::getItemName)
                .thenComparing(item -> Objects.toString(item.getMarketHashName(), ""));
        items.sort(Comparator.comparing(SteamInventoryItemDto::getTotalPrice,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byName));
        SteamInventoryItemDto mostValuable = items.stream().filter(item -> item.getUnitPrice() != null)
                .min(Comparator.comparing(SteamInventoryItemDto::getUnitPrice, Comparator.reverseOrder()).thenComparing(byName))
                .orElse(null);
        Profile profile = steam.fetchProfile(steamId);
        return SteamInventoryResponseDto.builder().steamId64(steamId).profileName(profile.name()).avatarUrl(profile.avatar())
                .totalValue(total.setScale(2, RoundingMode.HALF_UP)).totalItemCount(totalQuantity)
                .uniqueItemsCount(items.size()).unpricedItemCount(unpriced).items(List.copyOf(items))
                .categoryCounts(Collections.unmodifiableMap(categoryCounts)).categoryValues(Collections.unmodifiableMap(categoryValues))
                .mostValuableItem(mostValuable).build();
    }

    private Map<String, CsItem> findCatalogueItems(List<String> names) {
        Map<String, CsItem> found = new HashMap<>();
        for (int offset = 0; offset < names.size(); offset += CATALOGUE_BATCH_SIZE) {
            List<String> batch = names.subList(offset, Math.min(offset + CATALOGUE_BATCH_SIZE, names.size()));
            Set<String> requested = new HashSet<>(batch);
            // Prefer a true market hash match; use display-name fallback only for a legacy entry without a hash.
            catalogue.findCatalogueEntriesForMarketNames(batch).stream()
                    .sorted(Comparator.comparing(CsItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(item -> {
                        String name = item.getMarketHashName();
                        if (name == null || name.isBlank()) name = item.getName();
                        if (requested.contains(name)) {
                            CsItem existing = found.get(name);
                            if (existing == null || ((existing.getMarketHashName() == null || existing.getMarketHashName().isBlank())
                                    && item.getMarketHashName() != null && !item.getMarketHashName().isBlank())) found.put(name, item);
                        }
                    });
        }
        return found;
    }

    private String steamImage(String icon) {
        return icon != null && icon.matches("[A-Za-z0-9_-]{1,2048}")
                ? "https://community.akamai.steamstatic.com/economy/image/" + icon : null;
    }

    private String categorize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("★") || lower.contains("knife") || lower.contains("bayonet") || lower.contains("karambit")
                || lower.contains("gloves") || lower.contains("wraps")) return "Knives & Gloves";
        if (lower.contains("sticker") || lower.contains("patch") || lower.contains("graffiti")) return "Stickers & Patches";
        if (lower.contains("capsule")) return "Capsules";
        if (lower.contains("souvenir")) return "Souvenirs";
        if (lower.contains("case") || lower.contains("container") || lower.contains("package")) return "Cases";
        return lower.contains("|") ? "Weapon Skins" : "Other";
    }

    private record GroupKey(String marketName, DescriptionKey fallbackIdentity) {}

    private static class Group {
        final Description description;
        int quantity;
        int tradableQuantity;
        Group(Description description) { this.description = description; }
    }
}
