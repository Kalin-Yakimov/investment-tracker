package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.service.CsItemService;
import com.kalin.investment_tracker.service.ItemService;
import com.kalin.investment_tracker.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin("http://localhost:3000")
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private CsItemService csItemService;

    @PostMapping("/item")
    public Item newItem(@RequestBody Item newItem, @RequestHeader("Authorization") String token) {
        try {
            return itemService.addItem(newItem, token, jwtService);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process request: " + e.getMessage());
        }
    }

    @GetMapping("/get-user-items")
    public Page<ItemResponseDto> getAllUserItems(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "newest_date") String filter) {
        try {
            return itemService.getUserItems(token, jwtService, page, size, filter);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve user items: " + e.getMessage());
        }
    }

    @GetMapping("/portfolio-summary")
    public org.springframework.http.ResponseEntity<?> getPortfolioSummary(
            @RequestHeader("Authorization") String token) {
        try {
            return org.springframework.http.ResponseEntity.ok(itemService.getPortfolioSummary(token, jwtService));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/item/{id}")
    public ItemResponseDto getItemById(@PathVariable Long id) {
        return itemService.getItemById(id);
    }

    @GetMapping("/item/history")
    public Page<ItemResponseDto> getItemHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            return itemService.getItemHistory(token, jwtService, name, page, size);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve item history:" + e.getMessage());
        }
    }


    @PutMapping("/item/{id}")
    public Item updateItem(@RequestBody Item newItem, @PathVariable Long id, @RequestHeader("Authorization") String token) {
        try {
            return itemService.updateItem(id, newItem, token, jwtService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update item: " + e.getMessage());
        }
    }

    @DeleteMapping("/item/{id}")
    public String deleteItem(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        try {
            return itemService.deleteItem(id, token, jwtService);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete item: " + e.getMessage());
        }
    }

    @GetMapping("/items/search")
    public List<CsItem> searchCsItems(@RequestParam String query) {
        return csItemService.searchItems(query);
    }
}
