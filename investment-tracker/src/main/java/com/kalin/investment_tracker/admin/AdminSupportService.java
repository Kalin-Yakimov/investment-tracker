package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.auth.AuthenticationService;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.role.Role;
import com.kalin.investment_tracker.role.RoleNames;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.AuthSessionRepository;
import com.kalin.investment_tracker.security.AuthSessionService;
import com.kalin.investment_tracker.service.CsItemService;
import com.kalin.investment_tracker.service.SteamPriceService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final SteamPriceService steamPriceService;
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
        String cleanQuery = query == null ? "" : query.trim();
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 50),
                Sort.by(Sort.Direction.DESC, "createdDate")
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
        activityEventService.recordSupportAccess(
                actor,
                target,
                "SUPPORT_VIEW_OPENED",
                reason,
                "Opened the read-only support view",
                ipAddress
        );
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
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 50),
                Sort.by(Sort.Direction.DESC, "date")
        );
        Page<Item> items = itemRepository.findPortfolioForAdministration(userId, pageable);
        Map<String, CsItem> catalogue = loadCatalogue(items.getContent());
        return items.map(item -> new AdminDtos.PortfolioItem(
                item.getId(),
                item.getItemName(),
                item.getCsItemId(),
                item.getItemStatus(),
                item.getDate(),
                item.getQuantity(),
                item.getPrice(),
                catalogue.containsKey(item.getCsItemId()) ? catalogue.get(item.getCsItemId()).getImage() : null
        ));
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
        Map<String, CsItem> catalogue = loadCatalogue(items);
        return items.stream()
                .map(item -> new AdminDtos.TrackedItem(
                        item.getId(),
                        item.getItemName(),
                        item.getMarketHashName(),
                        item.getCsItemId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getShowInDashboard(),
                        item.getPriceNotification(),
                        catalogue.containsKey(item.getCsItemId())
                                ? catalogue.get(item.getCsItemId()).getImage()
                                : null
                ))
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
        User target = requireAccessibleTarget(actor, userId);
        recordSupportViewAccess(actor, target, reason, ipAddress);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return activityEventRepository
                .findByTargetUserIdOrderByCreatedAtDesc(target.getId(), pageable)
                .map(this::toActivity);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.Activity> getAdministrativeAudit(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        List<AdminDtos.Activity> groupedEvents = groupAdministrativeAuditEvents(
                activityEventRepository.findByCategoryInOrderByCreatedAtDesc(ADMINISTRATIVE_AUDIT_CATEGORIES)
        ).stream().map(this::toActivity).toList();

        int fromIndex = Math.min(safePage * safeSize, groupedEvents.size());
        int toIndex = Math.min(fromIndex + safeSize, groupedEvents.size());
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
        User target = requireUser(userId);
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
        User target = requireUser(userId);
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
        User target = requireAccessibleTarget(actor, userId);
        activityEventService.recordAdminAction(
                actor,
                target,
                "SESSIONS_REVOKED",
                reason,
                "Revoked all active authentication sessions",
                ipAddress
        );
        authSessionService.revokeAllForUser(target.getId());
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
        return new AdminDtos.ActionResult("A new activation code was sent");
    }

    public AdminDtos.ActionResult startCatalogueSync(User actor, String reason, String ipAddress) {
        String cleanReason = activityEventService.requireReason(reason);
        if ("RUNNING".equals(jobStatusService.get(SystemJobStatusService.CATALOGUE_SYNC).status())) {
            throw new IllegalArgumentException("A catalogue synchronization is already running");
        }
        activityEventService.recordSystemAction(
                actor,
                "CATALOGUE_SYNC_STARTED",
                cleanReason,
                "Started a manual CS item catalogue synchronization",
                ipAddress
        );
        jobStatusService.started(SystemJobStatusService.CATALOGUE_SYNC, "Manual synchronization is running");
        applicationTaskExecutor.execute(() -> {
            try {
                csItemService.fetchAndSaveItems();
                jobStatusService.succeeded(
                        SystemJobStatusService.CATALOGUE_SYNC,
                        "Catalogue synchronization completed successfully"
                );
            } catch (Exception exception) {
                jobStatusService.failed(SystemJobStatusService.CATALOGUE_SYNC, safeMessage(exception));
            }
        });
        return new AdminDtos.ActionResult("Catalogue synchronization started");
    }

    public AdminDtos.ActionResult startPriceUpdate(User actor, String reason, String ipAddress) {
        String cleanReason = activityEventService.requireReason(reason);
        if ("RUNNING".equals(jobStatusService.get(SystemJobStatusService.PRICE_UPDATE).status())) {
            throw new IllegalArgumentException("A price update is already running");
        }
        activityEventService.recordSystemAction(
                actor,
                "PRICE_UPDATE_STARTED",
                cleanReason,
                "Started a manual update for all tracked prices",
                ipAddress
        );
        jobStatusService.started(SystemJobStatusService.PRICE_UPDATE, "Manual price update is running");
        applicationTaskExecutor.execute(() -> {
            try {
                steamPriceService.forceUpdateAllTrackedItems();
                jobStatusService.succeeded(
                        SystemJobStatusService.PRICE_UPDATE,
                        "Tracked prices were updated successfully"
                );
            } catch (Exception exception) {
                jobStatusService.failed(SystemJobStatusService.PRICE_UPDATE, safeMessage(exception));
            }
        });
        return new AdminDtos.ActionResult("Price update started");
    }

    private User requireAccessibleTarget(User actor, Integer userId) {
        User target = requireUser(userId);
        if (!hasRole(actor, RoleNames.ADMIN) && hasRole(target, RoleNames.ADMIN)) {
            throw new AccessDeniedException("Support accounts cannot inspect administrator accounts");
        }
        return target;
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

    private Map<String, CsItem> loadCatalogue(Collection<Item> items) {
        Set<String> ids = items.stream()
                .map(Item::getCsItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return csItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CsItem::getId, Function.identity(), (first, ignored) -> first));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "The operation failed" : message;
    }
}
