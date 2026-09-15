package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.service.CsItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cs-items")
@RequiredArgsConstructor
public class CsItemController {

    private final CsItemService csItemService;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> syncItems() {
        csItemService.fetchAndSaveItems();
        return ResponseEntity.ok("Items synchronized successfully");
    }

    @GetMapping("/search")
    public ResponseEntity<List<CsItem>> searchItems(@RequestParam String query) {
        return ResponseEntity.ok(csItemService.searchItems(query));
    }
}
