package com.kalin.investment_tracker.shared.web;

import com.kalin.investment_tracker.tracking.TrackedItemController;

import com.kalin.investment_tracker.inventory.SteamInventoryController;

import com.kalin.investment_tracker.portfolio.PortfolioController;

import com.kalin.investment_tracker.catalogue.CsItemController;

import com.kalin.investment_tracker.admin.AdminSupportController;
import com.kalin.investment_tracker.admin.AdminSupportService;
import com.kalin.investment_tracker.handler.GlobalExceptionHandler;
import com.kalin.investment_tracker.portfolio.PortfolioService;
import com.kalin.investment_tracker.tracking.TrackedItemService;
import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.inventory.SteamInventoryService;
import com.kalin.investment_tracker.support.SupportTicketService;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiValidationTest {
    private MockMvc mvc;
    private PortfolioService portfolio;
    private TrackedItemService tracked;
    private AdminSupportService admin;
    private SupportTicketService tickets;
    private CsItemService catalogue;
    private SteamInventoryService inventory;

    @BeforeEach
    void setUp() {
        portfolio = mock(PortfolioService.class);
        tracked = mock(TrackedItemService.class);
        admin = mock(AdminSupportService.class);
        tickets = mock(SupportTicketService.class);
        catalogue = mock(CsItemService.class);
        inventory = mock(SteamInventoryService.class);
        User user = new User();
        user.setId(7);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioController(portfolio), new TrackedItemController(tracked),
                        new AdminSupportController(admin, tickets), new CsItemController(catalogue), new SteamInventoryController(inventory))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=not-a-number"})
    void invalidPortfolioPaginationIsRejectedBeforeService(String parameter) throws Exception {
        String[] parts = parameter.split("=");
        mvc.perform(get("/get-user-items").param(parts[0], parts[1])).andExpect(status().isBadRequest());
        verifyNoInteractions(portfolio);
    }

    @Test
    void defaultPortfolioPaginationAndResponseShapeAreUnchanged() throws Exception {
        when(portfolio.getUserItems(any(User.class), eq(0), eq(10), eq("newest_date"))).thenReturn(Page.empty());
        mvc.perform(get("/get-user-items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").value(0));
        verify(portfolio).getUserItems(any(User.class), eq(0), eq(10), eq("newest_date"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/item/0", "/item/-1", "/item/invalid", "/api/items/0/details", "/api/price-history/-1"})
    void invalidItemIdentifiersAreRejected(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest());
        verifyNoInteractions(portfolio, tracked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"itemIds\":[]}", "{\"itemIds\":[null]}", "{\"itemIds\":[0]}", "{\"itemIds\":[-1]}"})
    void invalidPriceUpdateIdsAreRejected(String body) throws Exception {
        mvc.perform(post("/api/prices/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }

    @Test
    void oversizedPriceUpdateBatchIsRejected() throws Exception {
        String body = "{\"itemIds\":[" + String.join(",", java.util.Collections.nCopies(1001, "1")) + "]}";
        mvc.perform(post("/api/prices/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"marketHashName\":\"   \"}", "{\"csItemId\":\" \"}"})
    void trackedCreationRequiresMeaningfulItemReference(String body) throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }

    @Test
    void catalogueOnlyTrackedCreationAndQuantityDefaultStillWork() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"csItemId\":\"skin-1\"}"))
                .andExpect(status().isOk());
        verify(tracked).addTrackedItem(argThat(body -> body.quantity() == 0 && "skin-1".equals(body.csItemId())), any(User.class));
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"csItemId\":\"skin-1\",\"quantity\":\"2\"}"))
                .andExpect(status().isOk());
        verify(tracked).addTrackedItem(argThat(body -> body.quantity() == 2 && "skin-1".equals(body.csItemId())), any(User.class));
    }

    @Test
    void trackedSettingsRejectUnsupportedMoneyPrecision() throws Exception {
        mvc.perform(put("/api/items/1").contentType(MediaType.APPLICATION_JSON).content("{\"priceNotification\":1.234}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }

    @Test
    void portfolioBodyValidationAndFormNumberStringsWorkWithValidatedPath() throws Exception {
        mvc.perform(put("/item/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Case\",\"itemStatus\":\"BOUGHT\",\"date\":\"10.10.2026\",\"quantity\":1,\"price\":1.234}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(portfolio);
        mvc.perform(put("/item/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Case\",\"itemStatus\":\"BOUGHT\",\"date\":\"10.10.2026\",\"quantity\":\"1\",\"price\":\"1.23\"}"))
                .andExpect(status().isOk());
        verify(portfolio).updateItem(eq(1L), argThat(body -> body.quantity() == 1
                && body.price().compareTo(new java.math.BigDecimal("1.23")) == 0), any(User.class));
    }

    @Test
    void missingHistoryNameAndSupportReasonGetBadRequestInsteadOfServerError() throws Exception {
        mvc.perform(get("/item/history")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/7")).andExpect(status().isBadRequest());
        verifyNoInteractions(portfolio, admin);
    }

    @Test
    void wrongTicketStatusAndMissingAdministrativeStateAreRejected() throws Exception {
        mvc.perform(get("/api/admin/tickets").param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/7/locked").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Investigating user request\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/7/support-role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Investigating user request\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(admin, tickets);
    }

    @Test
    void oversizedSearchAndInventoryInputAreRejected() throws Exception {
        mvc.perform(get("/api/cs-items/search").param("query", "a".repeat(256))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/inventory/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steamUrl\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalogue, inventory);
    }

    @Test
    void unsupportedContentTypeIsNotReportedAsInternalServerError() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.TEXT_PLAIN).content("not-json"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(tracked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/users", "/api/admin/tickets", "/api/admin/audit"})
    void administrativePaginationHasExplicitBounds(String url) throws Exception {
        mvc.perform(get(url).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(url).param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin, tickets);
    }

    @Test
    void explicitFalseAdministrativeStatesAreStillValid() throws Exception {
        mvc.perform(patch("/api/admin/users/7/locked").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locked\":false,\"reason\":\"Investigating user request\"}"))
                .andExpect(status().isOk());
        verify(admin).setAccountLocked(any(User.class), eq(7), eq(false), anyString(), anyString());
        mvc.perform(patch("/api/admin/users/7/support-role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"Investigating user request\"}"))
                .andExpect(status().isOk());
        verify(admin).setSupportRole(any(User.class), eq(7), eq(false), anyString(), anyString());
    }

    @Test
    void textAndMoneyValuesMustFitExistingDatabaseColumns() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketHashName\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/items/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceNotification\":10000000000000000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }

    @Test
    void fractionalQuantityAndIdsAreNotSilentlyTruncated() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"csItemId\":\"skin-1\",\"quantity\":1.5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/prices/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[1.5]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracked);
    }
}
