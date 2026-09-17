package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.dto.ApiMessageResponse;
import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.dto.PortfolioItemRequest;
import com.kalin.investment_tracker.dto.PortfolioSummaryDto;
import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.ItemStatus;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import static com.kalin.investment_tracker.utils.StringUtils.normalizeString;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final ActivityEventService activityEventService;

    @CacheEvict(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    public ItemResponseDto addItem(PortfolioItemRequest request, User user) {
        Item newItem = new Item();
        newItem.setUser(user);
        newItem.setCsItemId(request.csItemId());
        newItem.setItemStatus(request.itemStatus());
        newItem.setDate(request.date());
        newItem.setQuantity(request.quantity());
        newItem.setPrice(request.price());
        
        if (request.csItemId() != null && !request.csItemId().isBlank()) {
            CsItem csItem = findCatalogueItem(request.csItemId());
            newItem.setItemName(csItem.getName());
            newItem.setMarketHashName(csItem.getMarketHashName());
        } else {
            newItem.setItemName(normalizeString(request.itemName()));
        }

        Item savedItem = itemRepository.save(newItem);
        activityEventService.recordUserAction(
                user,
                "PORTFOLIO_ITEM_ADDED",
                "Added portfolio record '" + savedItem.getItemName() + "'",
                null
        );
        return mapToDto(savedItem);
    }

    @CacheEvict(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    public ItemResponseDto updateItem(Long id, PortfolioItemRequest request, User user) {
        // IDOR protection: query DB for item matching BOTH id AND authenticated userId
        return itemRepository.findByIdAndUserId(id, user.getId())
                .map(item -> {
                    if (request.csItemId() != null && !request.csItemId().isBlank()) {
                        CsItem csItem = findCatalogueItem(request.csItemId());
                        item.setItemName(csItem.getName());
                        item.setCsItemId(request.csItemId());
                        item.setMarketHashName(csItem.getMarketHashName());
                    } else {
                        item.setItemName(normalizeString(request.itemName()));
                        item.setCsItemId(null);
                        item.setMarketHashName(null);
                    }
                    item.setItemStatus(request.itemStatus());
                    item.setDate(request.date());
                    item.setQuantity(request.quantity());
                    item.setPrice(request.price());
                    Item savedItem = itemRepository.save(item);
                    activityEventService.recordUserAction(
                            user,
                            "PORTFOLIO_ITEM_UPDATED",
                            "Updated portfolio record '" + savedItem.getItemName() + "'",
                            null
                    );
                    return mapToDto(savedItem);
                })
                .orElseThrow(() -> new ItemNotFoundException(id));
    }

    public Page<ItemResponseDto> getUserItems(User user, int page, int size, String filter) {
        ItemStatus statusFilter = null;
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date");

        if ("bought".equalsIgnoreCase(filter)) {
            statusFilter = ItemStatus.BOUGHT;
        } else if ("sold".equalsIgnoreCase(filter)) {
            statusFilter = ItemStatus.SOLD;
        } else if ("oldest_date".equalsIgnoreCase(filter)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "date");
        } else if ("highest_quantity".equalsIgnoreCase(filter)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "quantity");
        } else if ("highest_unit_price".equalsIgnoreCase(filter)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "price");
        } else if ("lowest_unit_price".equalsIgnoreCase(filter)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "price");
        }

        Page<Item> items;
        if ("highest_total_price".equalsIgnoreCase(filter)) {
            items = itemRepository.findPortfolioOrderByTotalPriceDesc(
                    user.getId(), PageRequest.of(page, size));
        } else if ("lowest_total_price".equalsIgnoreCase(filter)) {
            items = itemRepository.findPortfolioOrderByTotalPriceAsc(
                    user.getId(), PageRequest.of(page, size));
        } else {
            Pageable pageable = PageRequest.of(page, size, sort);
            items = itemRepository.findByUserIdAndOptionalStatus(user.getId(), statusFilter, pageable);
        }
        
        return mapPageToDtoBatch(items);
    }

    @Cacheable(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    public PortfolioSummaryDto getPortfolioSummary(User user) {
        long count = itemRepository.countAllUserItems(user.getId());
        java.math.BigDecimal totalBought = itemRepository.sumTotalInvestmentValue(user.getId());

        return new PortfolioSummaryDto(
                count,
                totalBought != null ? totalBought : java.math.BigDecimal.ZERO
        );
    }

    public ItemResponseDto getItemById(Long id, User user) {
        Item item = itemRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(id));
        return mapToDto(item);
    }

    public Page<ItemResponseDto> getItemHistory(User user, String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdAndItemNameOrderByDateDesc(user.getId(), normalizeString(name), pageable);
        
        return mapPageToDtoBatch(items);
    }

    @CacheEvict(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    public ApiMessageResponse deleteItem(Long id, User user) {
        // IDOR protection: verify ownership at DB level before deletion
        Item item = itemRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(id));
        
        itemRepository.delete(item);
        activityEventService.recordUserAction(
                user,
                "PORTFOLIO_ITEM_DELETED",
                "Deleted portfolio record '" + item.getItemName() + "'",
                null
        );
        return new ApiMessageResponse("Item with id " + id + " has been deleted successfully.");
    }

    private Page<ItemResponseDto> mapPageToDtoBatch(Page<Item> items) {
        // Collect all CS item IDs on the page to fetch image URLs in a single query (prevents N+1)
        java.util.Set<String> csItemIds = items.getContent().stream()
                .map(Item::getCsItemId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<String, String> imageMap = new java.util.HashMap<>();
        if (!csItemIds.isEmpty()) {
            imageMap = csItemRepository.findAllById(csItemIds).stream()
                    .filter(item -> item.getImage() != null)
                    .collect(java.util.stream.Collectors.toMap(CsItem::getId, CsItem::getImage, (a, b) -> a));
        }

        final java.util.Map<String, String> finalImageMap = imageMap;
        return items.map(item -> ItemResponseDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .csItemId(item.getCsItemId())
                .itemStatus(item.getItemStatus())
                .date(item.getDate())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .image(item.getCsItemId() != null ? finalImageMap.get(item.getCsItemId()) : null)
                .build());
    }

    private ItemResponseDto mapToDto(Item item) {
        String imageUrl = null;
        if (item.getCsItemId() != null) {
            imageUrl = csItemRepository.findById(item.getCsItemId())
                    .map(CsItem::getImage)
                    .orElse(null);
        }
        
        return ItemResponseDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .csItemId(item.getCsItemId())
                .itemStatus(item.getItemStatus())
                .date(item.getDate())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .image(imageUrl)
                .build();
    }

    private CsItem findCatalogueItem(String id) {
        return csItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CS item was not found in the catalogue"));
    }
}
