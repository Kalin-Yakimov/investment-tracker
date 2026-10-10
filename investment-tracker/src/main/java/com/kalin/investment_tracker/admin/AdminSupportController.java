package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.support.SupportTicketDtos;
import com.kalin.investment_tracker.support.SupportTicketService;
import com.kalin.investment_tracker.support.SupportTicketStatus;
import com.kalin.investment_tracker.user.User;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import com.kalin.investment_tracker.validation.ApiRequestLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT')")
public class AdminSupportController {

    public static final String SUPPORT_REASON_HEADER = "X-Support-Reason";

    private final AdminSupportService service;
    private final SupportTicketService supportTicketService;

    @GetMapping("/overview")
    public ResponseEntity<AdminDtos.Overview> overview() {
        return ResponseEntity.ok(service.getOverview());
    }

    @GetMapping("/users")
    public ResponseEntity<AdminDtos.PageResponse<AdminDtos.UserSummary>> users(
            @RequestParam(defaultValue = "") @Size(max = ApiRequestLimits.ITEM_TEXT_LENGTH) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ApiRequestLimits.SUPPORT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(AdminDtos.PageResponse.from(service.searchUsers(query, page, size)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminDtos.UserDetails> userDetails(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestHeader(SUPPORT_REASON_HEADER) @NotBlank @Size(max = 500) String reason,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.getUserDetails(actor, userId, reason, request.getRemoteAddr()));
    }

    @GetMapping("/users/{userId}/portfolio")
    public ResponseEntity<AdminDtos.PageResponse<AdminDtos.PortfolioItem>> portfolio(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestHeader(SUPPORT_REASON_HEADER) @NotBlank @Size(max = 500) String reason,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ApiRequestLimits.SUPPORT_PAGE_SIZE) int size,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(AdminDtos.PageResponse.from(
                service.getPortfolio(actor, userId, reason, page, size, request.getRemoteAddr())
        ));
    }

    @GetMapping("/users/{userId}/tracked-items")
    public ResponseEntity<List<AdminDtos.TrackedItem>> trackedItems(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestHeader(SUPPORT_REASON_HEADER) @NotBlank @Size(max = 500) String reason,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.getTrackedItems(actor, userId, reason, request.getRemoteAddr()));
    }

    @GetMapping("/users/{userId}/sessions")
    public ResponseEntity<List<AdminDtos.Session>> sessions(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestHeader(SUPPORT_REASON_HEADER) @NotBlank @Size(max = 500) String reason,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.getSessions(actor, userId, reason, request.getRemoteAddr()));
    }

    @GetMapping("/users/{userId}/activity")
    public ResponseEntity<AdminDtos.PageResponse<AdminDtos.Activity>> userActivity(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestHeader(SUPPORT_REASON_HEADER) @NotBlank @Size(max = 500) String reason,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(ApiRequestLimits.SUPPORT_PAGE_SIZE) int size,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(AdminDtos.PageResponse.from(
                service.getUserActivity(actor, userId, reason, page, size, request.getRemoteAddr())
        ));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AdminDtos.PageResponse<AdminDtos.Activity>> audit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ApiRequestLimits.PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(AdminDtos.PageResponse.from(service.getAdministrativeAudit(page, size)));
    }

    @GetMapping("/tickets")
    public ResponseEntity<AdminDtos.PageResponse<SupportTicketDtos.TicketResponse>> tickets(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ApiRequestLimits.SUPPORT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(AdminDtos.PageResponse.from(
                supportTicketService.findAll(status, page, size)
        ));
    }

    @GetMapping("/tickets/new-count")
    public ResponseEntity<SupportTicketDtos.CountResponse> newTicketCount() {
        return ResponseEntity.ok(supportTicketService.countNew());
    }

    @PatchMapping("/tickets/{ticketId}/status")
    public ResponseEntity<SupportTicketDtos.TicketResponse> ticketStatus(
            @PathVariable @Positive Long ticketId,
            @RequestBody @Valid SupportTicketDtos.StatusRequest body
    ) {
        return ResponseEntity.ok(supportTicketService.updateStatus(ticketId, body.status()));
    }

    @DeleteMapping("/tickets/{ticketId}")
    public ResponseEntity<SupportTicketDtos.ActionResponse> removeTicket(
            @PathVariable @Positive Long ticketId
    ) {
        return ResponseEntity.ok(supportTicketService.remove(ticketId));
    }

    @PatchMapping("/users/{userId}/support-role")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AdminDtos.ActionResult> supportRole(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestBody @Valid AdminDtos.SupportRoleRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.setSupportRole(
                actor, userId, body.enabled(), body.reason(), request.getRemoteAddr()
        ));
    }

    @PatchMapping("/users/{userId}/locked")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AdminDtos.ActionResult> accountLocked(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestBody @Valid AdminDtos.LockRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.setAccountLocked(
                actor, userId, body.locked(), body.reason(), request.getRemoteAddr()
        ));
    }

    @PostMapping("/users/{userId}/revoke-sessions")
    public ResponseEntity<AdminDtos.ActionResult> revokeSessions(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestBody @Valid AdminDtos.ReasonRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(service.revokeSessions(
                actor, userId, body.reason(), request.getRemoteAddr()
        ));
    }

    @PostMapping("/users/{userId}/resend-activation")
    public ResponseEntity<AdminDtos.ActionResult> resendActivation(
            @AuthenticationPrincipal User actor,
            @PathVariable @Positive Integer userId,
            @RequestBody @Valid AdminDtos.ReasonRequest body,
            HttpServletRequest request
    ) throws MessagingException {
        return ResponseEntity.ok(service.resendActivation(
                actor, userId, body.reason(), request.getRemoteAddr()
        ));
    }

    @PostMapping("/system/catalogue-sync")
    public ResponseEntity<AdminDtos.ActionResult> catalogueSync(
            @AuthenticationPrincipal User actor,
            @RequestBody @Valid AdminDtos.ReasonRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(service.startCatalogueSync(
                actor, body.reason(), request.getRemoteAddr()
        ));
    }

    @PostMapping("/system/price-update")
    public ResponseEntity<AdminDtos.ActionResult> priceUpdate(
            @AuthenticationPrincipal User actor,
            @RequestBody @Valid AdminDtos.ReasonRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(service.startPriceUpdate(
                actor, body.reason(), request.getRemoteAddr()
        ));
    }
}
