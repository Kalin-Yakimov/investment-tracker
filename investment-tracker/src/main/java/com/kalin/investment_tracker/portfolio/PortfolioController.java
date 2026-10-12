package com.kalin.investment_tracker.portfolio;

import com.kalin.investment_tracker.shared.web.ApiMessageResponse;
import com.kalin.investment_tracker.shared.web.PageResponse;
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
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping("/item")
    public ResponseEntity<PortfolioItemResponseDto> newItem(
            @RequestBody @Valid PortfolioItemRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(portfolioService.addItem(request, user));
    }

    @GetMapping("/get-user-items")
    public ResponseEntity<PageResponse<PortfolioItemResponseDto>> getAllUserItems(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size,
            @RequestParam(required = false, defaultValue = "newest_date") @Size(max = 32) String filter) {
        return ResponseEntity.ok(PageResponse.from(portfolioService.getUserItems(user, page, size, filter)));
    }

    @GetMapping("/portfolio-summary")
    public ResponseEntity<PortfolioSummaryDto> getPortfolioSummary(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(user));
    }

    @GetMapping("/item/{id}")
    public ResponseEntity<PortfolioItemResponseDto> getItemById(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(portfolioService.getItemById(id, user));
    }

    @GetMapping("/item/history")
    public ResponseEntity<PageResponse<PortfolioItemResponseDto>> getItemHistory(
            @AuthenticationPrincipal User user,
            @RequestParam @NotBlank @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size) {
        return ResponseEntity.ok(PageResponse.from(portfolioService.getItemHistory(user, name, page, size)));
    }


    @PutMapping("/item/{id}")
    public ResponseEntity<PortfolioItemResponseDto> updateItem(
            @RequestBody @Valid PortfolioItemRequest request,
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(portfolioService.updateItem(id, request, user));
    }

    @DeleteMapping("/item/{id}")
    public ResponseEntity<ApiMessageResponse> deleteItem(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(portfolioService.deleteItem(id, user));
    }
}
