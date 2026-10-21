package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.auth.AuthenticationService;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.role.Role;
import com.kalin.investment_tracker.role.RoleNames;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.AuthSessionRepository;
import com.kalin.investment_tracker.security.AuthSessionService;
import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.kalin.investment_tracker.validation.ApiRequestLimits;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AdminSupportService {

    private static final List<String> ADMINISTRATIVE_AUDIT_CATEGORIES = List.of(
            ActivityEvent.ADMIN_ACTION,
            ActivityEvent.SUPPORT_ACCESS,
            ActivityEvent.SYSTEM
    );
    private static final int SUPPORT_ACCESS_GROUPING_MINUTES = 30;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuthSessionService authSessionService;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ActivityEventService activityEventService;
    private final AuthenticationService authenticationService;
    private final CsItemService csItemService;
    private final PriceFeedService priceFeedService;
    private final SystemJobStatusService jobStatusService;
    private final TaskExecutor applicationTaskExecutor;

    @Transactional(readOnly = true)
    public AdminDtos.Overview getOverview() {
        LocalDateTime now = LocalDateTime.now();
        return new AdminDtos.Overview(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByEnabledFalse(),
                userRepository.countByAccountLockedTrue(),
                authSessionRepository.countByRevokedAtIsNullAndExpiresAtAfter(now),
                itemRepository.countAllPortfolioItems(),
                itemRepository.countAllTrackedItems(),
                csItemRepository.count(),
                marketPriceSnapshotRepository.count(),
                marketPriceSnapshotRepository.findLatestObservedAt(),
                activityEventRepository.countByCategoryInAndCreatedAtAfter(
                        List.of(ActivityEvent.ADMIN_ACTION, ActivityEvent.SUPPORT_ACCESS),
                        now.minusHours(24)
                ),
                jobStatusService.get(SystemJobStatusService.CATALOGUE_SYNC),
                jobStatusService.get(SystemJobStatusService.PRICE_UPDATE)
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.UserSummary> searchUsers(String query, int page, int size) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.SUPPORT_PAGE_SIZE);
        String cleanQuery = query == null ? "" : query.trim();
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdDate", "id")
        );
        return userRepository.searchForAdministration(cleanQuery, pageable).map(this::toUserSummary);
    }

    @Transactional
    public AdminDtos.UserDetails getUserDetails(
            User actor,
            Integer userId,
            String reason,
            String ipAddress
    ) {
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        return new AdminDtos.UserDetails(
                toUserSummary(target),
                authSessionRepository.findLastActivityForUser(target.getId())
        );
    }

    @Transactional
    public Page<AdminDtos.PortfolioItem> getPortfolio(
            User actor,
            Integer userId,
            String reason,
            int page,
            int size,
            String ipAddress
    ) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.SUPPORT_PAGE_SIZE);
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "date", "id")
        );
        Page<Item> items = itemRepository.findPortfolioForAdministration(userId, pageable);
        return items.map(item -> {
            var catalogueItem = item.getCatalogueItem();
            return new AdminDtos.PortfolioItem(
                    item.getId(),
                    item.getItemName(),
                    catalogueItem != null ? catalogueItem.getId() : null,
                    item.getItemStatus(),
                    item.getDate(),
                    item.getQuantity(),
                    item.getPrice(),
                    catalogueItem != null ? catalogueItem.getImage() : null
            );
        });
    }

    @Transactional
    public List<AdminDtos.TrackedItem> getTrackedItems(
            User actor,
            Integer userId,
            String reason,
            String ipAddress
    ) {
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        List<Item> items = itemRepository.findSteamTrackedItemsByUserId(userId);
        return items.stream()
                .map(item -> {
                    var catalogueItem = item.getCatalogueItem();
                    return new AdminDtos.TrackedItem(
                            item.getId(),
                            item.getItemName(),
                            item.getMarketHashName(),
                            catalogueItem != null ? catalogueItem.getId() : null,
                            item.getQuantity(),
                            item.getPrice(),
                            item.getShowInDashboard(),
                            item.getPriceNotification(),
                            catalogueItem != null ? catalogueItem.getImage() : null
                    );
                })
                .toList();
    }

    @Transactional
    public List<AdminDtos.Session> getSessions(
            User actor,
            Integer userId,
            String reason,
            String ipAddress
    ) {
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        boolean actorIsAdmin = hasRole(actor, RoleNames.ADMIN);
        return authSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                        target.getId(),
                        LocalDateTime.now()
                )
                .stream()
                .map(session -> new AdminDtos.Session(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getLastUsedAt(),
                        session.getExpiresAt(),
                        session.isRemembered(),
                        session.getUserAgent(),
                        actorIsAdmin ? session.getIpAddress() : null
                ))
                .toList();
    }

    @Transactional
    public Page<AdminDtos.Activity> getUserActivity(
            User actor,
            Integer userId,
            String reason,
            int page,
            int size,
            String ipAddress
    ) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.SUPPORT_PAGE_SIZE);
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        Pageable pageable = PageRequest.of(page, size);
        return activityEventRepository
                .findByTargetUserIdOrderByCreatedAtDesc(target.getId(), pageable)
                .map(this::toActivity);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.Activity> getAdministrativeAudit(int page, int size) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size);
        List<AdminDtos.Activity> groupedEvents = groupAdministrativeAuditEvents(
                activityEventRepository.findByCategoryInOrderByCreatedAtDesc(ADMINISTRATIVE_AUDIT_CATEGORIES)
        ).stream().map(this::toActivity).toList();

        int fromIndex = (int) Math.min(pageable.getOffset(), groupedEvents.size());
        int toIndex = Math.min(fromIndex + size, groupedEvents.size());
        return new PageImpl<>(groupedEvents.subList(fromIndex, toIndex), pageable, groupedEvents.size());
    }

    @Transactional
    public AdminDtos.ActionResult setSupportRole(
            User actor,
            Integer userId,
            boolean enabled,
            String reason,
            String ipAddress
    ) {
        requireAdmin(actor);
        User target = userRepository.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (hasRole(target, RoleNames.ADMIN)) {
            throw new IllegalArgumentException("Administrator accounts do not need the SUPPORT role");
        }

        Role supportRole = roleRepository.findByName(RoleNames.SUPPORT)
                .orElseThrow(() -> new IllegalStateException("SUPPORT role is not initialized"));
        List<Role> roles = new ArrayList<>(target.getRoles());
        boolean currentlyEnabled = roles.stream().anyMatch(role -> RoleNames.SUPPORT.equals(role.getName()));

        if (enabled && !currentlyEnabled) {
            roles.add(supportRole);
        } else if (!enabled && currentlyEnabled) {
            roles.removeIf(role -> RoleNames.SUPPORT.equals(role.getName()));
        }
        target.setRoles(roles);
        userRepository.save(target);

        if (currentlyEnabled != enabled) {
            authSessionService.revokeAllForUser(target.getId());
        }
        activityEventService.recordAdminAction(
                actor,
                target,
                enabled ? "SUPPORT_ROLE_GRANTED" : "SUPPORT_ROLE_REMOVED",
                reason,
                "Changed support access and revoked existing sessions",
                ipAddress
        );
        return new AdminDtos.ActionResult(enabled ? "Support role granted" : "Support role removed");
    }

    @Transactional
    public AdminDtos.ActionResult setAccountLocked(
            User actor,
            Integer userId,
            boolean locked,
            String reason,
            String ipAddress
    ) {
        requireAdmin(actor);
        User target = userRepository.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("You cannot lock your own administrator account");
        }
        if (hasRole(target, RoleNames.ADMIN)) {
            throw new IllegalArgumentException("Administrator accounts cannot be locked from this panel");
        }

        target.setAccountLocked(locked);
        userRepository.save(target);
        if (locked) {
            authSessionService.revokeAllForUser(target.getId());
        }
        activityEventService.recordAdminAction(
                actor,
                target,
                locked ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED",
                reason,
                locked ? "Locked the account and revoked its active sessions" : "Unlocked the account",
                ipAddress
        );
        return new AdminDtos.ActionResult(locked ? "Account locked" : "Account unlocked");
    }

    @Transactional
    public AdminDtos.ActionResult revokeSessions(
            User actor,
            Integer userId,
            String reason,
            String ipAddress
    ) {
        User target = userRepository.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
        requireAccessibleTarget(actor, target);
        authSessionService.revokeAllForUser(target.getId());
        activityEventService.recordAdminAction(
                actor,
                target,
                "SESSIONS_REVOKED",
                reason,
                "Revoked all active authentication sessions",
                ipAddress
        );
        return new AdminDtos.ActionResult("All active sessions were revoked");
    }

    @Transactional
    public AdminDtos.ActionResult resendActivation(
            User actor,
            Integer userId,
            String reason,
            String ipAddress
    ) throws MessagingException {
        User target = requireAccessibleTarget(actor, userId);
        if (target.isEnabled()) {
            throw new IllegalArgumentException("The account is already activated");
        }
        authenticationService.resendActivationFor(target);
        activityEventService.recordAdminAction(
                actor,
                target,
                "ACTIVATION_EMAIL_RESENT",
                reason,
                "Requested a new account activation code",
                ipAddress
        );
        return new AdminDtos.ActionResult("A new activation code was requested");
    }

    public AdminDtos.ActionResult startCatalogueSync(User actor, String reason, String ipAddress) {
        submitSystemJob(actor, reason, ipAddress, SystemJobStatusService.CATALOGUE_SYNC,
                "CATALOGUE_SYNC_STARTED", "Started a manual CS item catalogue synchronization", () -> {
                    csItemService.fetchAndSaveItems();
                    return "Catalogue synchronization completed successfully";
                });
        return new AdminDtos.ActionResult("Catalogue synchronization started");
    }

    public AdminDtos.ActionResult startPriceUpdate(User actor, String reason, String ipAddress) {
        submitSystemJob(actor, reason, ipAddress, SystemJobStatusService.PRICE_UPDATE,
                "PRICE_UPDATE_STARTED", "Started a manual update for all tracked prices", () -> {
            int count = priceFeedService.forceUpdateAllTrackedItems();
            return count > 0 ? "Stored " + count + " daily price observations"
                    : "No newer price observations were available from CSGOTrader";
        });
        return new AdminDtos.ActionResult("Price update started");
    }

    private void submitSystemJob(User actor, String reason, String ipAddress, String job, String action,
                                 String description, Supplier<String> operation) {
        String cleanReason = activityEventService.requireReason(reason);
        if (!jobStatusService.tryStart(job, "Manual operation is running")) {
            throw new IllegalArgumentException("This operation is already running");
        }
        try {
            activityEventService.recordSystemAction(actor, action, cleanReason, description, ipAddress);
            applicationTaskExecutor.execute(() -> {
                try { jobStatusService.succeeded(job, operation.get()); }
                catch (Exception failure) { jobStatusService.failed(job, safeMessage(failure)); }
            });
        } catch (RejectedExecutionException busy) {
            jobStatusService.failed(job, "The background task queue is full; try again later");
            throw new ExternalServiceException("The background task queue is full; try again later");
        } catch (RuntimeException failure) {
            jobStatusService.failed(job, "The operation could not be started");
            throw failure;
        }
    }

    private User requireAccessibleTarget(User actor, Integer userId) {
        User target = requireUser(userId);
        requireAccessibleTarget(actor, target);
        return target;
    }

    private void requireAccessibleTarget(User actor, User target) {
        if (!hasRole(actor, RoleNames.ADMIN) && hasRole(target, RoleNames.ADMIN)) {
            throw new AccessDeniedException("Support accounts cannot inspect administrator accounts");
        }
    }

    private void recordSupportViewAccess(User actor, User target, String reason, String ipAddress) {
        activityEventService.recordSupportAccess(
                actor,
                target,
                "SUPPORT_VIEW_OPENED",
                reason,
                "Opened the read-only support view",
                ipAddress
        );
    }

    private List<ActivityEvent> groupAdministrativeAuditEvents(List<ActivityEvent> events) {
        Map<SupportAccessKey, LocalDateTime> latestAccessByCase = new HashMap<>();
        List<ActivityEvent> groupedEvents = new ArrayList<>();

        for (ActivityEvent event : events) {
            if (!ActivityEvent.SUPPORT_ACCESS.equals(event.getCategory())) {
                groupedEvents.add(event);
                continue;
            }

            SupportAccessKey key = new SupportAccessKey(
                    event.getActorUser() == null ? null : event.getActorUser().getId(),
                    event.getTargetUser() == null ? null : event.getTargetUser().getId(),
                    event.getReason()
            );
            LocalDateTime previousAccess = latestAccessByCase.put(key, event.getCreatedAt());
            boolean belongsToExistingCase = previousAccess != null
                    && Duration.between(event.getCreatedAt(), previousAccess).toMinutes()
                    <= SUPPORT_ACCESS_GROUPING_MINUTES;
            if (!belongsToExistingCase) {
                groupedEvents.add(event);
            }
        }
        return groupedEvents;
    }

    private record SupportAccessKey(Integer actorId, Integer targetId, String reason) {
    }

    private User requireUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
    }

    private void requireAdmin(User actor) {
        if (!hasRole(actor, RoleNames.ADMIN)) {
            throw new AccessDeniedException("Administrator access is required");
        }
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(role -> roleName.equals(role.getName()));
    }

    private AdminDtos.UserSummary toUserSummary(User user) {
        return new AdminDtos.UserSummary(
                user.getId(),
                user.fullName(),
                user.getEmail(),
                user.isEnabled(),
                user.isAccountLocked(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                user.getCreatedDate(),
                user.getLastModifiedDate(),
                itemRepository.countAllUserItems(user.getId()),
                itemRepository.countTrackedItemsByUserId(user.getId()),
                authSessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user.getId(),
                        LocalDateTime.now()
                )
        );
    }

    private AdminDtos.Activity toActivity(ActivityEvent event) {
        return new AdminDtos.Activity(
                event.getId(),
                event.getCategory(),
                event.getEventType(),
                event.getActorUser() == null ? "System" : event.getActorUser().getEmail(),
                event.getTargetUser() == null ? null : event.getTargetUser().getEmail(),
                event.getReason(),
                event.getDetails(),
                event.getIpAddress(),
                event.getCreatedAt()
        );
    }

    private String safeMessage(Exception exception) {
        return exception instanceof ExternalServiceException ? exception.getMessage() : "The operation failed; try again later";
    }
}
