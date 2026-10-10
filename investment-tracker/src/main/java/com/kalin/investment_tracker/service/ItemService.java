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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kalin.investment_tracker.validation.ApiRequestLimits;

import java.util.Locale;

import static com.kalin.investment_tracker.utils.StringUtils.normalizeString;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final ActivityEventService activityEventService;

    @CacheEvict(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    @Transactional
    public ItemResponseDto addItem(PortfolioItemRequest request, User user) {
        Item newItem = new Item();
        newItem.setUser(user);
        newItem.setItemStatus(request.itemStatus());
        newItem.setDate(request.date());
        newItem.setQuantity(request.quantity());
        newItem.setPrice(request.price());
        
        if (request.csItemId() != null && !request.csItemId().isBlank()) {
            CsItem csItem = findCatalogueItem(request.csItemId());
            newItem.setCatalogueItem(csItem);
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
    @Transactional
    public ItemResponseDto updateItem(Long id, PortfolioItemRequest request, User user) {
        return itemRepository.findPortfolioItemByIdAndUserId(id, user.getId())
                .map(item -> {
                    if (request.csItemId() != null && !request.csItemId().isBlank()) {
                        CsItem csItem = findCatalogueItem(request.csItemId());
                        item.setItemName(csItem.getName());
                        item.setCatalogueItem(csItem);
                        item.setMarketHashName(csItem.getMarketHashName());
                    } else {
                        item.setItemName(normalizeString(request.itemName()));
                        item.setCatalogueItem(null);
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
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.PAGE_SIZE);
        String selectedFilter = filter == null ? "newest_date" : filter.trim().toLowerCase(Locale.ROOT);
        ItemStatus statusFilter = switch (selectedFilter) {
            case "bought" -> ItemStatus.BOUGHT;
            case "sold" -> ItemStatus.SOLD;
            default -> null;
        };
        Sort sort = switch (selectedFilter) {
            case "newest_date", "bought", "sold", "highest_total_price", "lowest_total_price" -> Sort.by(Sort.Direction.DESC, "date", "id");
            case "oldest_date" -> Sort.by(Sort.Direction.ASC, "date", "id");
            case "highest_quantity" -> Sort.by(Sort.Direction.DESC, "quantity", "id");
            case "highest_unit_price" -> Sort.by(Sort.Direction.DESC, "price", "id");
            case "lowest_unit_price" -> Sort.by(Sort.Direction.ASC, "price", "id");
            default -> throw new IllegalArgumentException("Unsupported portfolio filter");
        };

        Page<Item> items;
        if ("highest_total_price".equals(selectedFilter)) {
            items = itemRepository.findPortfolioOrderByTotalPriceDesc(
                    user.getId(), PageRequest.of(page, size));
        } else if ("lowest_total_price".equals(selectedFilter)) {
            items = itemRepository.findPortfolioOrderByTotalPriceAsc(
                    user.getId(), PageRequest.of(page, size));
        } else {
            Pageable pageable = PageRequest.of(page, size, sort);
            items = itemRepository.findByUserIdAndOptionalStatus(user.getId(), statusFilter, pageable);
        }
        
        return mapPageToDto(items);
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
        Item item = itemRepository.findPortfolioItemByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(id));
        return mapToDto(item);
    }

    public Page<ItemResponseDto> getItemHistory(User user, String name, int page, int size) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdAndItemNameOrderByDateDesc(user.getId(), normalizeString(name), pageable);
        
        return mapPageToDto(items);
    }

    @CacheEvict(value = com.kalin.investment_tracker.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE, key = "#user.id")
    @Transactional
    public ApiMessageResponse deleteItem(Long id, User user) {
        Item item = itemRepository.findPortfolioItemByIdAndUserId(id, user.getId())
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

    private Page<ItemResponseDto> mapPageToDto(Page<Item> items) {
        return items.map(this::mapToDto);
    }

    private ItemResponseDto mapToDto(Item item) {
        CsItem catalogueItem = item.getCatalogueItem();
        return ItemResponseDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .csItemId(catalogueItem != null ? catalogueItem.getId() : null)
                .itemStatus(item.getItemStatus())
                .date(item.getDate())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .image(catalogueItem != null ? catalogueItem.getImage() : null)
                .build();
    }

    private CsItem findCatalogueItem(String id) {
        return csItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CS item was not found in the catalogue"));
    }
}
