package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.CsItemDto;
import com.kalin.investment_tracker.dto.ApiMessageResponse;
import com.kalin.investment_tracker.service.CsItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CsItemController {

    private final CsItemService csItemService;

    @PostMapping("/cs-items/sync")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiMessageResponse> syncItems() {
        csItemService.fetchAndSaveItems();
        return ResponseEntity.ok(new ApiMessageResponse("Items synchronized successfully"));
    }

    @GetMapping({"/cs-items/search", "/items/search", "/api/cs-items/search"})
    public ResponseEntity<List<CsItemDto>> searchItems(@RequestParam String query) {
        return ResponseEntity.ok(csItemService.searchItems(query).stream()
                .map(CsItemDto::from)
                .toList());
    }
}
