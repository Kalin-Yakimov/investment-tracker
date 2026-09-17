package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.ApiMessageResponse;
import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.dto.PageResponse;
import com.kalin.investment_tracker.dto.PortfolioItemRequest;
import com.kalin.investment_tracker.dto.PortfolioSummaryDto;
import com.kalin.investment_tracker.service.ItemService;
import com.kalin.investment_tracker.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping("/item")
    public ResponseEntity<ItemResponseDto> newItem(
            @RequestBody @Valid PortfolioItemRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(itemService.addItem(request, user));
    }

    @GetMapping("/get-user-items")
    public ResponseEntity<PageResponse<ItemResponseDto>> getAllUserItems(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "newest_date") String filter) {
        return ResponseEntity.ok(PageResponse.from(itemService.getUserItems(user, page, size, filter)));
    }

    @GetMapping("/portfolio-summary")
    public ResponseEntity<PortfolioSummaryDto> getPortfolioSummary(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.getPortfolioSummary(user));
    }

    @GetMapping("/item/{id}")
    public ResponseEntity<ItemResponseDto> getItemById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.getItemById(id, user));
    }

    @GetMapping("/item/history")
    public ResponseEntity<PageResponse<ItemResponseDto>> getItemHistory(
            @AuthenticationPrincipal User user,
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PageResponse.from(itemService.getItemHistory(user, name, page, size)));
    }


    @PutMapping("/item/{id}")
    public ResponseEntity<ItemResponseDto> updateItem(
            @RequestBody @Valid PortfolioItemRequest request,
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.updateItem(id, request, user));
    }

    @DeleteMapping("/item/{id}")
    public ResponseEntity<ApiMessageResponse> deleteItem(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.deleteItem(id, user));
    }
}
