package com.kalin.investment_tracker.support;

import com.kalin.investment_tracker.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService service;

    @PostMapping
    public ResponseEntity<SupportTicketDtos.TicketResponse> create(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid SupportTicketDtos.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, request));
    }
}
