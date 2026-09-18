package com.kalin.investment_tracker.support;

import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository repository;

    @Transactional
    public SupportTicketDtos.TicketResponse create(
            User user,
            SupportTicketDtos.CreateRequest request
    ) {
        SupportTicket ticket = SupportTicket.builder()
                .user(user)
                .accountUsername(user.fullName())
                .accountEmail(user.getEmail())
                .contactEmail(request.email().trim())
                .message(request.message().trim())
                .status(SupportTicketStatus.NEW)
                .build();
        return toResponse(repository.save(ticket));
    }

    @Transactional(readOnly = true)
    public Page<SupportTicketDtos.TicketResponse> findAll(
            SupportTicketStatus status,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 50)
        );
        Page<SupportTicket> tickets = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return tickets.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupportTicketDtos.CountResponse countNew() {
        return new SupportTicketDtos.CountResponse(repository.countByStatus(SupportTicketStatus.NEW));
    }

    @Transactional
    public SupportTicketDtos.TicketResponse updateStatus(
            Long ticketId,
            SupportTicketStatus status
    ) {
        SupportTicket ticket = requireTicket(ticketId);
        ticket.setStatus(status);
        return toResponse(repository.save(ticket));
    }

    @Transactional
    public SupportTicketDtos.ActionResponse remove(Long ticketId) {
        SupportTicket ticket = requireTicket(ticketId);
        repository.delete(ticket);
        return new SupportTicketDtos.ActionResponse("Contact request removed");
    }

    private SupportTicket requireTicket(Long ticketId) {
        return repository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Contact request was not found"));
    }

    private SupportTicketDtos.TicketResponse toResponse(SupportTicket ticket) {
        return new SupportTicketDtos.TicketResponse(
                ticket.getId(),
                ticket.getUser() != null ? ticket.getUser().getId() : null,
                ticket.getAccountUsername(),
                ticket.getAccountEmail(),
                ticket.getContactEmail(),
                ticket.getMessage(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
