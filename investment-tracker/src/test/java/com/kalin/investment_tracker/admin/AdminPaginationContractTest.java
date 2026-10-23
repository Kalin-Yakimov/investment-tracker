package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.handler.GlobalExceptionHandler;
import com.kalin.investment_tracker.support.SupportTicketService;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPaginationContractTest {
    private AdminSupportService service;
    private SupportTicketService tickets;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminSupportService.class);
        tickets = mock(SupportTicketService.class);
        User actor = new User();
        actor.setId(7);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new AdminSupportController(service, tickets))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/users", "/users/42/portfolio", "/users/42/activity", "/audit", "/tickets"})
    void everyPagedSupportEndpointKeepsTheSameJsonContract(String endpoint) throws Exception {
        // Empty page beyond the available records retains the original metadata.
        Page<?> page = new PageImpl<>(List.of(), PageRequest.of(1, 2), 2);
        switch (endpoint) {
            case "/users" -> doReturn(page).when(service).searchUsers("", 1, 2);
            case "/users/42/portfolio" -> doReturn(page).when(service)
                    .getPortfolio(any(User.class), eq(42), eq("Investigating a report"), eq(1), eq(2), anyString());
            case "/users/42/activity" -> doReturn(page).when(service)
                    .getUserActivity(any(User.class), eq(42), eq("Investigating a report"), eq(1), eq(2), anyString());
            case "/audit" -> doReturn(page).when(service).getAdministrativeAudit(1, 2);
            case "/tickets" -> doReturn(page).when(tickets).findAll(null, 1, 2);
            default -> throw new IllegalArgumentException(endpoint);
        }

        mvc.perform(get("/api/admin" + endpoint).param("page", "1").param("size", "2")
                        .header(AdminSupportController.SUPPORT_REASON_HEADER, "Investigating a report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }
}
