package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.shared.web.ApiMessageResponse;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshotDto;
import com.kalin.investment_tracker.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.kalin.investment_tracker.shared.web.PageResponse;
import com.kalin.investment_tracker.validation.ApiRequestLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TrackedItemController {

    private final TrackedItemService trackedItemService;

    @GetMapping("/items")
    public ResponseEntity<PageResponse<TrackedItemDto>> getAllItems(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "dashboard", defaultValue = "false") boolean dashboard,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(trackedItemService.getTrackedItems(user, dashboard, page, size)));
    }

    @PostMapping("/items")
    public ResponseEntity<TrackedItemDto> addItem(
            @RequestBody @Valid TrackedItemCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.addTrackedItem(request, user));
    }

    @PostMapping("/prices/update")
    public ResponseEntity<PriceUpdateResponse> updatePrices(
            @RequestBody @Valid PriceUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.requestPriceUpdate(request.itemIds(), user));
    }

    @GetMapping("/price-history/{itemId}")
    public ResponseEntity<List<MarketPriceSnapshotDto>> getPriceHistory(
            @PathVariable @Positive Long itemId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.getPriceHistory(itemId, user));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<TrackedItemDto> updateItem(
            @PathVariable @Positive Long id,
            @RequestBody @Valid TrackedItemUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.updateTrackedItem(id, request, user));
    }

    @PatchMapping("/items/{id}/bookmark")
    public ResponseEntity<BookmarkResponse> toggleBookmark(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.toggleBookmark(id, user));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiMessageResponse> deleteItem(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.deleteTrackedItem(id, user));
    }

    @PostMapping("/prices/validate")
    public ResponseEntity<MarketItemValidationResponse> validateItem(
            @RequestBody @Valid MarketItemValidationRequest request
    ) {
        return ResponseEntity.ok(trackedItemService.validateMarketItem(request.marketHashName()));
    }

    @GetMapping("/items/{id}/details")
    public ResponseEntity<TrackedItemDto> getItemDetails(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(trackedItemService.getTrackedItemDetails(id, user));
    }
}
