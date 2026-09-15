package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.service.CsItemService;
import com.kalin.investment_tracker.service.ItemService;
import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final CsItemService csItemService;

    @PostMapping("/item")
    public Item newItem(@RequestBody Item newItem, @AuthenticationPrincipal User user) {
        return itemService.addItem(newItem, user);
    }

    @GetMapping("/get-user-items")
    public Page<ItemResponseDto> getAllUserItems(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "newest_date") String filter) {
        return itemService.getUserItems(user, page, size, filter);
    }

    @GetMapping("/portfolio-summary")
    public org.springframework.http.ResponseEntity<?> getPortfolioSummary(
            @AuthenticationPrincipal User user) {
        return org.springframework.http.ResponseEntity.ok(itemService.getPortfolioSummary(user));
    }

    @GetMapping("/item/{id}")
    public ItemResponseDto getItemById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return itemService.getItemById(id, user);
    }

    @GetMapping("/item/history")
    public Page<ItemResponseDto> getItemHistory(
            @AuthenticationPrincipal User user,
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return itemService.getItemHistory(user, name, page, size);
    }


    @PutMapping("/item/{id}")
    public Item updateItem(
            @RequestBody Item newItem,
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return itemService.updateItem(id, newItem, user);
    }

    @DeleteMapping("/item/{id}")
    public String deleteItem(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return itemService.deleteItem(id, user);
    }

    @GetMapping("/items/search")
    public List<CsItem> searchCsItems(@RequestParam String query) {
        return csItemService.searchItems(query);
    }
}
