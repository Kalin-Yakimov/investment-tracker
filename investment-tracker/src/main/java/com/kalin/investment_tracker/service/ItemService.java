package com.kalin.investment_tracker.service;

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
        } else {
            newItem.setItemName(normalizeString(newItem.getItemName()));
        }
        
        ItemStatus itemStatus = ItemStatus.valueOf(newItem.getItemStatus().toString());
        newItem.setItemStatus(itemStatus);
        return itemRepository.save(newItem);
    }

    public Item updateItem(Long id, Item newItem, String token, JwtService jwtService) {
        User user = validateUser(token, jwtService);
        return itemRepository.findById(id)
                .map(item -> {
                    if (!item.getUser().getId().equals(user.getId())) {
                        throw new RuntimeException("You are not authorized to update this item.");
                    }
                    if (newItem.getCsItemId() != null) {
                        CsItem csItem = csItemRepository.findById(newItem.getCsItemId())
                                .orElseThrow(() -> new RuntimeException("CS Item not found"));
                        item.setItemName(csItem.getName());
                        item.setCsItemId(newItem.getCsItemId());
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

    public Page<Item> getUserItems(String token, JwtService jwtService, int page, int size) {
        User user = validateUser(token, jwtService);
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdOrderByDateDesc(user.getId(), pageable);
        //formating each item name
        items.forEach(item -> item.setItemName(formatString(item.getItemName())));
        return items;
    }

    public Item getItemById(Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
        item.setItemName(formatString(item.getItemName()));
        return item;
    }

    public Page<Item> getItemHistory(String token, JwtService jwtService, String name, int page, int size) {
        User user = validateUser(token, jwtService);
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByUserIdAndItemNameOrderByDateDesc(user.getId(), normalizeString(name), pageable);
        items.forEach(item -> item.setItemName(formatString(item.getItemName())));
        return  items;
    }

    public String deleteItem(Long id, String token, JwtService jwtService) {
        User user = validateUser(token, jwtService);
        return itemRepository.findById(id)
                .map(item -> {
                    if (!item.getUser().getId().equals(user.getId())) {
                        throw new RuntimeException("You are not authorized to delete this item.");
                    }
                    itemRepository.deleteById(id);
                    return "Item with id" + id + "has been deleted successfully.";
                })
                .orElseThrow(() -> new ItemNotFoundException(id));
    }
}
