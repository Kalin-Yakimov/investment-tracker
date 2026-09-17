package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.auth.AuthenticationService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
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
    @Mock private SteamPriceService steamPriceService;
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
        when(userRepository.findById(2)).thenReturn(Optional.of(target));
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
