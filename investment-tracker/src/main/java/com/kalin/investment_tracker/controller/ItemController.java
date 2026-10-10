package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.ApiMessageResponse;
import com.kalin.investment_tracker.dto.ItemResponseDto;
import com.kalin.investment_tracker.dto.PageResponse;
import com.kalin.investment_tracker.dto.PortfolioItemRequest;
import com.kalin.investment_tracker.dto.PortfolioSummaryDto;
import com.kalin.investment_tracker.service.ItemService;
import com.kalin.investment_tracker.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.kalin.investment_tracker.validation.ApiRequestLimits;
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size,
            @RequestParam(required = false, defaultValue = "newest_date") @Size(max = 32) String filter) {
        return ResponseEntity.ok(PageResponse.from(itemService.getUserItems(user, page, size, filter)));
    }

    @GetMapping("/portfolio-summary")
    public ResponseEntity<PortfolioSummaryDto> getPortfolioSummary(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.getPortfolioSummary(user));
    }

    @GetMapping("/item/{id}")
    public ResponseEntity<ItemResponseDto> getItemById(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.getItemById(id, user));
    }

    @GetMapping("/item/history")
    public ResponseEntity<PageResponse<ItemResponseDto>> getItemHistory(
            @AuthenticationPrincipal User user,
            @RequestParam @NotBlank @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size) {
        return ResponseEntity.ok(PageResponse.from(itemService.getItemHistory(user, name, page, size)));
    }


    @PutMapping("/item/{id}")
    public ResponseEntity<ItemResponseDto> updateItem(
            @RequestBody @Valid PortfolioItemRequest request,
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.updateItem(id, request, user));
    }

    @DeleteMapping("/item/{id}")
    public ResponseEntity<ApiMessageResponse> deleteItem(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(itemService.deleteItem(id, user));
    }
}
