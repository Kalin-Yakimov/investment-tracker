package com.kalin.investment_tracker.support;

import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository repository;

    @InjectMocks
    private SupportTicketService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7);
        user.setUsername("Kalin");
        user.setEmail("account@example.com");
    }

    @Test
    void createAssociatesAuthenticatedUserAndKeepsSubmittedContactEmail() {
        when(repository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket saved = invocation.getArgument(0);
            saved.setId(12L);
            saved.initializeTimestamps();
            return saved;
        });

        var result = service.create(
                user,
                new SupportTicketDtos.CreateRequest(
                        "reply-to@example.com",
                        "The tracked price is not updating for one of my items."
                )
        );

        assertEquals(12L, result.id());
        assertEquals(7, result.userId());
        assertEquals("account@example.com", result.accountEmail());
        assertEquals("reply-to@example.com", result.contactEmail());
        assertEquals(SupportTicketStatus.NEW, result.status());
    }

    @Test
    void updateStatusChangesExistingTicket() {
        SupportTicket ticket = ticket();
        when(repository.findById(12L)).thenReturn(Optional.of(ticket));
        when(repository.save(ticket)).thenReturn(ticket);

        var result = service.updateStatus(12L, SupportTicketStatus.IN_PROGRESS);

        assertEquals(SupportTicketStatus.IN_PROGRESS, result.status());
        verify(repository).save(ticket);
    }

    @Test
    void removeDeletesOnlySelectedTicket() {
        SupportTicket ticket = ticket();
        when(repository.findById(12L)).thenReturn(Optional.of(ticket));

        var result = service.remove(12L);

        assertEquals("Contact request removed", result.message());
        verify(repository).delete(ticket);
    }

    @Test
    void countNewReturnsOnlyNewContactRequests() {
        when(repository.countByStatus(SupportTicketStatus.NEW)).thenReturn(3L);

        var result = service.countNew();

        assertEquals(3L, result.count());
        verify(repository).countByStatus(SupportTicketStatus.NEW);
    }

    private SupportTicket ticket() {
        SupportTicket ticket = SupportTicket.builder()
                .id(12L)
                .user(user)
                .accountUsername(user.fullName())
                .accountEmail(user.getEmail())
                .contactEmail("reply-to@example.com")
                .message("The tracked price is not updating for one of my items.")
                .status(SupportTicketStatus.NEW)
                .build();
        ticket.initializeTimestamps();
        return ticket;
    }
}
