package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.ItemStatus;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import static com.kalin.investment_tracker.utils.StringUtils.formatString;
import static com.kalin.investment_tracker.utils.StringUtils.normalizeString;

@Service
public class ItemService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CsItemRepository csItemRepository;

    public User validateUser(String token, JwtService jwtService) {
        String jwt = token.substring(7);
        String userIdStr = jwtService.extractUserId(jwt);
        return userRepository.findById(Integer.parseInt(userIdStr))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public Item addItem(Item newItem, String token, JwtService jwtService) {
        User user = validateUser(token, jwtService);
        newItem.setUser(user);
        
        if (newItem.getCsItemId() != null) {
            CsItem csItem = csItemRepository.findById(newItem.getCsItemId())
                    .orElseThrow(() -> new RuntimeException("CS Item not found"));
            newItem.setItemName(csItem.getName());
            newItem.setMarketHashName(csItem.getMarketHashName());
        } else {
            newItem.setItemName(normalizeString(newItem.getItemName()));
        }
        
        ItemStatus itemStatus = ItemStatus.valueOf(newItem.getItemStatus().toString());
        newItem.setItemStatus(itemStatus);
        return itemRepository.save(newItem);
    }

    public Item updateItem(Long id, Item newItem, String token, JwtService jwtService) {
        User user = validateUser(token, jwtService);
        // IDOR protection: query DB for item matching BOTH id AND authenticated userId
        return itemRepository.findByIdAndUserId(id, user.getId())
                .map(item -> {
                    if (newItem.getCsItemId() != null) {
                        CsItem csItem = csItemRepository.findById(newItem.getCsItemId())
                                .orElseThrow(() -> new RuntimeException("CS Item not found"));
                        item.setItemName(csItem.getName());
                        item.setCsItemId(newItem.getCsItemId());
                        item.setMarketHashName(csItem.getMarketHashName());
                    } else {
                        item.setItemName(normalizeString(newItem.getItemName()));
                    }
                    item.setItemStatus(newItem.getItemStatus());
                    item.setDate(newItem.getDate());
                    item.setQuantity(newItem.getQuantity());
                    item.setPrice(newItem.getPrice());
                    return itemRepository.save(item);
                })
                .orElseThrow(() -> new ItemNotFoundException(id));
    }

    public Page<ItemResponseDto> getUserItems(String token, JwtService jwtService, int page, int size) {
        User user = validateUser(token, jwtService);
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdOrderByDateDesc(user.getId(), pageable);
        
        return mapPageToDtoBatch(items);
    }

    public ItemResponseDto getItemById(Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
        return mapToDto(item);
    }

    public Page<ItemResponseDto> getItemHistory(String token, JwtService jwtService, String name, int page, int size) {
        User user = validateUser(token, jwtService);
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdAndItemNameOrderByDateDesc(user.getId(), normalizeString(name), pageable);
        
        return mapPageToDtoBatch(items);
    }

    public String deleteItem(Long id, String token, JwtService jwtService) {
        User user = validateUser(token, jwtService);
        // IDOR protection: verify ownership at DB level before deletion
        Item item = itemRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(id));
        
        itemRepository.delete(item);
        return "Item with id " + id + " has been deleted successfully.";
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
}
