package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.auth.AuthenticationService;
import com.kalin.investment_tracker.exception.ExternalServiceException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class AdminSupportServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private CsItemRepository csItemRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private AuthSessionService authSessionService;
    @Mock private MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private ActivityEventService activityEventService;
    @Mock private AuthenticationService authenticationService;
    @Mock private CsItemService csItemService;
    @Mock private PriceFeedService priceFeedService;
    @Mock private SystemJobStatusService jobStatusService;
    @Mock private TaskExecutor applicationTaskExecutor;

    @InjectMocks
    private AdminSupportService service;

    @Test
    void supportCannotInspectAdministratorAccount() {
        User support = user(1, RoleNames.SUPPORT);
        User administrator = user(2, RoleNames.ADMIN);
        when(userRepository.findById(2)).thenReturn(Optional.of(administrator));

        assertThrows(
                AccessDeniedException.class,
                () -> service.getUserDetails(support, 2, "Investigating a reported problem", "127.0.0.1")
        );
        verify(activityEventService, never()).recordSupportAccess(
                support,
                administrator,
                "SUPPORT_VIEW_OPENED",
                "Investigating a reported problem",
                "Opened the read-only support view",
                "127.0.0.1"
        );
    }

    @Test
    void administratorCanGrantSupportRoleAndExistingSessionsAreRevoked() {
        User administrator = user(1, RoleNames.ADMIN);
        User target = user(2, RoleNames.USER);
        Role supportRole = Role.builder().name(RoleNames.SUPPORT).build();
        when(userRepository.findLockedById(2)).thenReturn(Optional.of(target));
        when(roleRepository.findByName(RoleNames.SUPPORT)).thenReturn(Optional.of(supportRole));

        service.setSupportRole(
                administrator,
                2,
                true,
                "Assigned to the support team",
                "127.0.0.1"
        );

        assertTrue(target.getRoles().stream().anyMatch(role -> RoleNames.SUPPORT.equals(role.getName())));
        verify(userRepository).save(target);
        verify(authSessionService).revokeAllForUser(2);
        verify(activityEventService).recordAdminAction(
                administrator,
                target,
                "SUPPORT_ROLE_GRANTED",
                "Assigned to the support team",
                "Changed support access and revoked existing sessions",
                "127.0.0.1"
        );
    }

    @Test
    void openingUserDetailsUsesTheSameSupportAccessAuditAsOtherReadOnlyViews() {
        User administrator = user(1, RoleNames.ADMIN);
        User target = user(2, RoleNames.USER);
        when(userRepository.findById(2)).thenReturn(Optional.of(target));

        var result = service.getUserDetails(administrator, 2, "Investigating a report", "127.0.0.1");

        assertEquals(2, result.user().id());
        verify(activityEventService).recordSupportAccess(administrator, target,
                "SUPPORT_VIEW_OPENED", "Investigating a report",
                "Opened the read-only support view", "127.0.0.1");
    }

    @Test
    void supportCannotChangeAnotherUsersRole() {
        User support = user(1, RoleNames.SUPPORT);

        assertThrows(
                AccessDeniedException.class,
                () -> service.setSupportRole(
                        support,
                        2,
                        true,
                        "Assigned to the support team",
                        "127.0.0.1"
                )
        );
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sessionRevocationLocksAndChecksTargetBeforeRevokingAndAuditing() {
        User support = user(1, RoleNames.SUPPORT);
        User target = user(2, RoleNames.USER);
        when(userRepository.findLockedById(2)).thenReturn(Optional.of(target));
        service.revokeSessions(support, 2, "Investigating stolen session", "127.0.0.1");
        var ordered = org.mockito.Mockito.inOrder(userRepository, authSessionService, activityEventService);
        ordered.verify(userRepository).findLockedById(2);
        ordered.verify(authSessionService).revokeAllForUser(2);
        ordered.verify(activityEventService).recordAdminAction(support, target, "SESSIONS_REVOKED",
                "Investigating stolen session", "Revoked all active authentication sessions", "127.0.0.1");
    }

    @Test
    void supportCannotRevokeAdministratorSessions() {
        User support = user(1, RoleNames.SUPPORT);
        when(userRepository.findLockedById(2)).thenReturn(Optional.of(user(2, RoleNames.ADMIN)));
        assertThrows(AccessDeniedException.class,
                () -> service.revokeSessions(support, 2, "Investigating session", "127.0.0.1"));
        verify(authSessionService, never()).revokeAllForUser(any());
    }

    @Test
    void auditEntriesAreGroupedBeforePagination() {
        User administrator = user(1, RoleNames.ADMIN);
        User target = user(2, RoleNames.USER);
        LocalDateTime now = LocalDateTime.now();
        List<ActivityEvent> events = List.of(
                supportAccess(administrator, target, now),
                supportAccess(administrator, target, now.minusMinutes(1)),
                supportAccess(administrator, target, now.minusMinutes(2)),
                ActivityEvent.builder()
                        .actorUser(administrator)
                        .targetUser(target)
                        .category(ActivityEvent.ADMIN_ACTION)
                        .eventType("ACCOUNT_UNLOCKED")
                        .reason("Resolved the reported access problem")
                        .createdAt(now.minusMinutes(3))
                        .build(),
                supportAccess(administrator, target, now.minusMinutes(40))
        );
        when(activityEventRepository.findByCategoryInOrderByCreatedAtDesc(any())).thenReturn(events);

        Page<AdminDtos.Activity> firstPage = service.getAdministrativeAudit(0, 2);
        Page<AdminDtos.Activity> secondPage = service.getAdministrativeAudit(1, 2);

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getNumberOfElements());
        assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void veryLargeAuditPageReturnsAnEmptyPageWithoutIntegerOverflow() {
        when(activityEventRepository.findByCategoryInOrderByCreatedAtDesc(any())).thenReturn(List.of());
        Page<AdminDtos.Activity> result = service.getAdministrativeAudit(Integer.MAX_VALUE, 100);
        assertTrue(result.isEmpty());
        assertEquals(Integer.MAX_VALUE, result.getNumber());
    }

    @Test
    void invalidAdministrativePaginationIsRejectedRatherThanSilentlyClamped() {
        assertThrows(IllegalArgumentException.class, () -> service.getAdministrativeAudit(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.getAdministrativeAudit(0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.searchUsers("", 0, 51));
    }

    @Test
    void rejectedBackgroundJobDoesNotRemainRunning() {
        when(activityEventService.requireReason("reason")).thenReturn("reason");
        when(jobStatusService.tryStart(SystemJobStatusService.PRICE_UPDATE, "Manual operation is running")).thenReturn(true);
        doThrow(new TaskRejectedException("queue full")).when(applicationTaskExecutor).execute(any());
        assertThrows(ExternalServiceException.class, () -> service.startPriceUpdate(user(1, RoleNames.ADMIN), "reason", "127.0.0.1"));
        verify(jobStatusService).failed(SystemJobStatusService.PRICE_UPDATE, "The background task queue is full; try again later");
        verify(priceFeedService, never()).forceUpdateAllTrackedItems();
    }

    @Test
    void unsuccessfulCatalogueJobNeverReportsSuccessOrLeaksDetails() {
        when(activityEventService.requireReason("reason")).thenReturn("reason");
        when(jobStatusService.tryStart(SystemJobStatusService.CATALOGUE_SYNC, "Manual operation is running")).thenReturn(true);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(applicationTaskExecutor).execute(any());
        doThrow(new IllegalStateException("private SQL details")).when(csItemService).fetchAndSaveItems();
        service.startCatalogueSync(user(1, RoleNames.ADMIN), "reason", "127.0.0.1");
        verify(jobStatusService).failed(SystemJobStatusService.CATALOGUE_SYNC, "The operation failed; try again later");
        verify(jobStatusService, never()).succeeded(any(), any());
    }

    @Test
    void secondJobIsRejectedBeforeSubmittingOrRecordingAnotherStart() {
        when(activityEventService.requireReason("reason")).thenReturn("reason");
        assertThrows(IllegalArgumentException.class, () -> service.startPriceUpdate(user(1, RoleNames.ADMIN), "reason", "127.0.0.1"));
        verify(applicationTaskExecutor, never()).execute(any());
    }

    private User user(int id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setRoles(new ArrayList<>(List.of(Role.builder().name(roleName).build())));
        return user;
    }

    private ActivityEvent supportAccess(User actor, User target, LocalDateTime createdAt) {
        return ActivityEvent.builder()
                .actorUser(actor)
                .targetUser(target)
                .category(ActivityEvent.SUPPORT_ACCESS)
                .eventType("SUPPORT_VIEW_OPENED")
                .reason("Opening the same support case")
                .createdAt(createdAt)
                .build();
    }
}
