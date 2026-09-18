package com.kalin.investment_tracker.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class SupportTicketDtos {

    private SupportTicketDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "Contact email is required")
            @Email(message = "Enter a valid contact email")
            @Size(max = 255, message = "Contact email is too long")
            String email,

            @NotBlank(message = "Message is required")
            @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters")
            String message
    ) {
    }

    public record StatusRequest(
            @NotNull(message = "Ticket status is required")
            SupportTicketStatus status
    ) {
    }

    public record TicketResponse(
            Long id,
            Integer userId,
            String accountUsername,
            String accountEmail,
            String contactEmail,
            String message,
            SupportTicketStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ActionResponse(String message) {
    }

    public record CountResponse(long count) {
    }
}
