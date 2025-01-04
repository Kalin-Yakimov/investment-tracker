package com.kalin.investment_tracker.controller;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.service.ItemService;
import com.kalin.investment_tracker.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

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
    public Page<Item> getAllUserItems(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            return itemService.getUserItems(token, jwtService, page, size);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve user items: " + e.getMessage());
        }
    }

    @GetMapping("/item/{id}")
    public Item getItemById(@PathVariable Long id) {
        return itemService.getItemById(id);
    }

    @GetMapping("/item/history")
    public Page<Item> getItemHistory(
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
}


