package com.kalin.investment_tracker.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.email.EmailTemplateName;
import com.kalin.investment_tracker.pricing.MarketPricePersistenceService;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import com.kalin.investment_tracker.pricing.PriceFeedSnapshot;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.support.SupportTicketRepository;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters, controllers, services and commits; only outbound dependencies are replaced. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", "app.bootstrap-admin-email=",
        "spring.mail.username=workflow@example.com", "spring.mail.password=test-only",
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "security.jwt.issuer=workflow-test", "security.jwt.audience=workflow-test-web",
        "security.auth.cookie-secure=false", "security.auth.cookie-same-site=Strict",
        "app.cors.allowed-origins=http://localhost:3000",
        "price.update.initial-delay-ms=86400000", "price.history.prune-cron=-",
        "security.auth.cleanup-cron=-", "security.verification.cleanup-cron=-"
})
@AutoConfigureMockMvc(printOnlyOnFailure = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationWorkflowIntegrationTest {
    private static final String PASSWORD = "workflow-test-password";
    private static final AtomicInteger CLIENT = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CsItemRepository catalogue;
    @Autowired SupportTicketRepository tickets;
    @Autowired PasswordEncoder passwords;
    @Autowired MarketPricePersistenceService marketPrices;
    @MockitoBean CsItemService catalogueSync;
    @MockitoBean PriceFeedService priceFeed;
    @MockitoBean EmailService email;
    @MockitoBean RestTemplate outboundHttp;
    @MockitoBean(name = "verificationEmailExecutor") TaskExecutor verificationExecutor;
    @MockitoBean(name = "applicationTaskExecutor") TaskExecutor jobExecutor;
    private String clientAddress;

    @BeforeEach
    void prepare() {
        clientAddress = "192.0.2." + CLIENT.incrementAndGet();
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(verificationExecutor).execute(any(Runnable.class));
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(jobExecutor).execute(any(Runnable.class));
    }

    @AfterEach
    void neverContactRealProviders() {
        verifyNoInteractions(outboundHttp);
    }

    @Test
    void anonymousRequestsCannotReadDataOrSubmitContactRequests() throws Exception {
        for (String route : List.of("/get-user-items", "/api/items", "/api/admin/overview")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
        mvc.perform(body(post("/api/contact"), Map.of("email", "reply@example.com", "message", "Please help with this record")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT", "ADMIN"})
    void roleMatrixIsEnforcedByTheRealSecurityChain(String role) throws Exception {
        Login login = login(account(role));
        mvc.perform(authorized(get("/get-user-items"), login)).andExpect(status().isOk());
        mvc.perform(authorized(get("/api/items"), login)).andExpect(status().isOk());
        mvc.perform(authorized(get("/api/admin/overview"), login))
                .andExpect(status().is(role.equals("USER") ? 403 : 200));
        mvc.perform(authorized(get("/api/admin/audit"), login))
                .andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
        if (!role.equals("ADMIN")) {
            mvc.perform(authorized(body(patch("/api/admin/users/{id}/support-role", account("USER").getId()),
                    Map.of("enabled", true, "reason", "Integration test")), login))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void registrationActivationAndRealPasswordAuthenticationWorkTogether() throws Exception {
        String name = "workflow-" + UUID.randomUUID().toString().substring(0, 12);
        String address = name + "@example.com";
        mvc.perform(body(post("/api/v1/auth/register"), Map.of("username", name, "email", address, "password", PASSWORD)))
                .andExpect(status().isAccepted());
        User created = users.findByEmail(address).orElseThrow();
        assertFalse(created.isEnabled());
        assertNotEquals(PASSWORD, created.getPassword());
        mvc.perform(body(post("/api/v1/auth/authenticate"), credentials(created, PASSWORD)))
                .andExpect(status().isUnauthorized());
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendEmail(eq(address), anyString(), eq(EmailTemplateName.ACTIVATE_ACCOUNT),
                anyString(), code.capture(), anyString());
        mvc.perform(body(post("/api/v1/auth/activate-account"), Map.of("email", address, "code", code.getValue())))
                .andExpect(status().isOk());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), login(created))).andExpect(status().isOk());
    }

    @Test
    void portfolioChangesArePrivateAndCatalogueMetadataStaysUntouched() throws Exception {
        Login owner = login(account("USER"));
        Login stranger = login(account("USER"));
        CsItem item = catalogueItem();
        JsonNode created = response(mvc.perform(authorized(body(post("/item"), portfolio(item, 2)), owner))
                .andExpect(status().isOk()).andReturn());
        long id = created.get("id").asLong();
        mvc.perform(authorized(get("/item/{id}", id), stranger)).andExpect(status().isNotFound());
        mvc.perform(authorized(body(put("/item/{id}", id), portfolio(item, 5)), stranger)).andExpect(status().isNotFound());
        mvc.perform(authorized(delete("/item/{id}", id), stranger)).andExpect(status().isNotFound());
        mvc.perform(authorized(body(put("/item/{id}", id), portfolio(item, 5)), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5));
        mvc.perform(authorized(get("/get-user-items").param("filter", "highest_quantity"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(authorized(delete("/item/{id}", id), owner)).andExpect(status().isOk());
        CsItem preserved = catalogue.findById(item.getId()).orElseThrow();
        assertEquals(item.getMarketHashName(), preserved.getMarketHashName());
        assertEquals(item.getImage(), preserved.getImage());
    }

    @Test
    void trackedItemsShareDailyObservationsButKeepOwnershipAndPinningSeparate() throws Exception {
        Login owner = login(account("USER"));
        Login stranger = login(account("USER"));
        CsItem item = catalogueItem();
        long id = response(mvc.perform(authorized(body(post("/api/items"),
                Map.of("csItemId", item.getId(), "quantity", 1, "showInDashboard", false)), owner))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
        mvc.perform(authorized(body(post("/api/items"), Map.of("csItemId", item.getId(), "quantity", 1)), owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("You are already tracking this item"));
        mvc.perform(authorized(get("/item/{id}", id), owner)).andExpect(status().isNotFound());
        mvc.perform(authorized(get("/api/price-history/{id}", id), stranger)).andExpect(status().isNotFound());
        mvc.perform(authorized(patch("/api/items/{id}/bookmark", id), stranger)).andExpect(status().isNotFound());
        mvc.perform(authorized(patch("/api/items/{id}/bookmark", id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(true));
        mvc.perform(authorized(get("/api/items").param("dashboard", "true"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));

        Instant previousDay = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS).plusSeconds(3600);
        Instant today = previousDay.plus(1, ChronoUnit.DAYS);
        // No backfilling: fixtures represent two actual provider publications with an unchanged price.
        assertEquals(1, marketPrices.persistFeed(publication(item, previousDay, "previous")));
        assertEquals(1, marketPrices.persistFeed(publication(item, today, "today")));
        assertEquals(0, marketPrices.persistFeed(publication(item, today, "today")));
        mvc.perform(authorized(get("/api/price-history/{id}", id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].dateExtracted").value(previousDay.toString()))
                .andExpect(jsonPath("$[1].dateExtracted").value(today.toString()));
        mvc.perform(authorized(delete("/api/items/{id}", id), owner)).andExpect(status().isOk());
        assertTrue(catalogue.existsById(item.getId()));
    }

    @Test
    void userContactRequestCanBeProcessedBySupportButNotByAnotherUser() throws Exception {
        User user = account("USER");
        Login owner = login(user);
        Login stranger = login(account("USER"));
        Login support = login(account("SUPPORT"));
        long before = tickets.countByStatus(com.kalin.investment_tracker.support.SupportTicketStatus.NEW);
        long id = response(mvc.perform(authorized(body(post("/api/contact"),
                Map.of("email", "reply@example.com", "message", "Please check this portfolio record")), owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.status").value("NEW")).andReturn()).get("id").asLong();
        mvc.perform(authorized(get("/api/admin/tickets"), stranger)).andExpect(status().isForbidden());
        mvc.perform(authorized(body(patch("/api/admin/tickets/{id}/status", id), Map.of("status", "RESOLVED")), stranger))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(delete("/api/admin/tickets/{id}", id), stranger)).andExpect(status().isForbidden());
        mvc.perform(authorized(get("/api/admin/tickets/new-count"), support))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(before + 1));
        mvc.perform(authorized(body(patch("/api/admin/tickets/{id}/status", id), Map.of("status", "IN_PROGRESS")), support))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(authorized(get("/api/admin/tickets/new-count"), support))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(before));
        mvc.perform(authorized(body(patch("/api/admin/tickets/{id}/status", id), Map.of("status", "RESOLVED")), support))
                .andExpect(status().isOk());
        mvc.perform(authorized(delete("/api/admin/tickets/{id}", id), support)).andExpect(status().isOk());
        assertFalse(tickets.existsById(id));
    }

    @Test
    void supportRequiresAReasonAndCannotInspectAdministratorAccounts() throws Exception {
        Login support = login(account("SUPPORT"));
        User target = account("USER");
        mvc.perform(authorized(get("/api/admin/users/{id}", target.getId()), support)).andExpect(status().isBadRequest());
        mvc.perform(authorized(get("/api/admin/users/{id}", target.getId())
                        .header("X-Support-Reason", "Investigating a contact request"), support))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(target.getEmail()))
                .andExpect(jsonPath("$.user.password").doesNotExist());
        mvc.perform(authorized(get("/api/admin/users/{id}", account("ADMIN").getId())
                        .header("X-Support-Reason", "Integration test"), support))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingAndRemovingSupportRevokesOldTokensAndTakesEffectAfterLogin() throws Exception {
        Login admin = login(account("ADMIN"));
        User target = account("USER");
        Login original = login(target);
        for (boolean granted : List.of(true, false)) {
            mvc.perform(authorized(body(patch("/api/admin/users/{id}/support-role", target.getId()),
                    Map.of("enabled", granted, "reason", "Integration test role change")), admin))
                    .andExpect(status().isOk());
            mvc.perform(authorized(get("/api/v1/auth/validate-token"), original)).andExpect(status().isUnauthorized());
            original = login(target);
            mvc.perform(authorized(get("/api/admin/overview"), original)).andExpect(status().is(granted ? 200 : 403));
        }
    }

    @Test
    void signOutRevokesAccessAndRefreshOnBothDevices() throws Exception {
        User user = account("USER");
        Login first = login(user);
        Login second = login(user);
        mvc.perform(authorized(cookieAuth("/api/v1/auth/logout"), first)).andExpect(status().isNoContent());
        for (Login device : List.of(first, second)) {
            mvc.perform(authorized(get("/api/v1/auth/validate-token"), device)).andExpect(status().isUnauthorized());
            mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(device.refresh())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void refreshRotationAndReplayInvalidateTheSessionThroughHttp() throws Exception {
        Login initial = login(account("USER"));
        MvcResult rotated = mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(initial.refresh()))
                .andExpect(status().isOk()).andReturn();
        Login replacement = readLogin(rotated);
        assertNotEquals(initial.refresh().getValue(), replacement.refresh().getValue());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), replacement)).andExpect(status().isOk());
        mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(initial.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), replacement)).andExpect(status().isUnauthorized());
        mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(replacement.refresh())).andExpect(status().isUnauthorized());
    }

    @Test
    void lockingAnAccountInvalidatesAccessAndPreventsFreshLogin() throws Exception {
        Login admin = login(account("ADMIN"));
        User target = account("USER");
        Login targetLogin = login(target);
        mvc.perform(authorized(body(patch("/api/admin/users/{id}/locked", target.getId()),
                Map.of("locked", true, "reason", "Integration test lock")), admin)).andExpect(status().isOk());
        mvc.perform(authorized(get("/get-user-items"), targetLogin)).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/v1/auth/authenticate"), credentials(target, PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(targetLogin.refresh())).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRevokesBothDevicesAndRequiresTheNewPassword() throws Exception {
        User user = account("USER");
        Login first = login(user);
        Login second = login(user);
        String changedPassword = "changed-workflow-password";
        mvc.perform(authorized(body(patch("/api/v1/auth/change-password"), Map.of(
                "currentPassword", PASSWORD, "newPassword", changedPassword,
                "confirmationPassword", changedPassword)), first)).andExpect(status().isOk());
        for (Login device : List.of(first, second)) {
            mvc.perform(authorized(get("/api/v1/auth/validate-token"), device)).andExpect(status().isUnauthorized());
            mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(device.refresh())).andExpect(status().isUnauthorized());
        }
        mvc.perform(body(post("/api/v1/auth/authenticate"), credentials(user, PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(authorized(get("/get-user-items"), login(user, changedPassword))).andExpect(status().isOk());
    }

    @Test
    void expectedPasswordErrorsReturn400WithoutEndingTheExistingSession() throws Exception {
        User user = account("USER");
        Login active = login(user);
        mvc.perform(authorized(body(patch("/api/v1/auth/change-password"), Map.of(
                "currentPassword", "incorrect-password", "newPassword", "next-password",
                "confirmationPassword", "next-password")), active))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.businessErrorCode").value(300));
        mvc.perform(authorized(body(patch("/api/v1/auth/change-password"), Map.of(
                "currentPassword", PASSWORD, "newPassword", "next-password",
                "confirmationPassword", "different-password")), active))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.businessErrorCode").value(301));
        assertTrue(passwords.matches(PASSWORD, users.findById(user.getId()).orElseThrow().getPassword()));
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), active)).andExpect(status().isOk());
        mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(active.refresh())).andExpect(status().isOk());
    }

    @Test
    void legacySixCharacterPasswordCanSignInAndBeReplacedWithExactlyTenCharacters() throws Exception {
        User user = account("USER");
        user.setPassword(passwords.encode("legacy"));
        users.saveAndFlush(user);
        Login active = login(user, "legacy");
        String next = "new-pass10";
        assertEquals(10, next.length());
        mvc.perform(authorized(body(patch("/api/v1/auth/change-password"), Map.of(
                "currentPassword", "legacy", "newPassword", next, "confirmationPassword", next)), active))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), active)).andExpect(status().isUnauthorized());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), login(user, next))).andExpect(status().isOk());
    }

    @Test
    void registrationRejectsWeakOrOverlongPasswordsBeforePersistingAnAccount() throws Exception {
        String name = "policy-" + UUID.randomUUID().toString().substring(0, 12);
        String address = name + "@example.invalid";
        for (String invalid : List.of("a".repeat(9), "a".repeat(73), "я".repeat(37), "😀".repeat(9))) {
            mvc.perform(body(post("/api/v1/auth/register"), Map.of("username", name, "email", address, "password", invalid)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors").isNotEmpty());
            assertTrue(users.findByEmail(address).isEmpty());
        }
        mvc.perform(body(post("/api/v1/auth/register"), Map.of("username", name, "email", address, "password", "new-pass10")))
                .andExpect(status().isAccepted());
        assertTrue(passwords.matches("new-pass10", users.findByEmail(address).orElseThrow().getPassword()));
    }

    @Test
    void resettingPasswordKeepsTheCodeUsableAfterValidationFailureAndRevokesBothDevicesOnSuccess() throws Exception {
        User user = account("USER");
        Login first = login(user);
        Login second = login(user);
        mvc.perform(body(post("/api/v1/auth/forgot-password"), Map.of("email", user.getEmail())))
                .andExpect(status().isOk());
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendEmail(eq(user.getEmail()), anyString(), eq(EmailTemplateName.RESET_PASSWORD),
                anyString(), code.capture(), anyString());
        mvc.perform(body(post("/api/v1/auth/reset-password"), Map.of(
                "email", user.getEmail(), "code", code.getValue(), "newPassword", "too-short")))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), first)).andExpect(status().isOk());
        String next = "new-pass10";
        mvc.perform(body(post("/api/v1/auth/reset-password"), Map.of(
                "email", user.getEmail(), "code", code.getValue(), "newPassword", next)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        for (Login device : List.of(first, second)) {
            mvc.perform(authorized(get("/api/v1/auth/validate-token"), device)).andExpect(status().isUnauthorized());
            mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(device.refresh())).andExpect(status().isUnauthorized());
        }
        mvc.perform(body(post("/api/v1/auth/reset-password"), Map.of(
                "email", user.getEmail(), "code", code.getValue(), "newPassword", "other-password")))
                .andExpect(status().isBadRequest());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), login(user, next))).andExpect(status().isOk());
    }

    @Test
    void unchangedUnicodeUsernameProducesAUsableTokenAndConflictsReturn409() throws Exception {
        User owner = account("USER");
        String displayName = "Калин_" + UUID.randomUUID().toString().substring(0, 8);
        owner.setUsername(displayName);
        users.saveAndFlush(owner);
        Login active = login(owner);
        MvcResult changed = mvc.perform(authorized(body(patch("/api/v1/auth/change-username"),
                        Map.of("newUsername", displayName)), active))
                .andExpect(status().isOk()).andReturn();
        String token = response(changed).get("token").asString();
        var payload = json.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(displayName, payload.get("fullName").asString());
        assertEquals(owner.getEmail(), payload.get("email").asString());
        mvc.perform(get("/api/v1/auth/validate-token").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        User other = account("USER");
        mvc.perform(authorized(body(patch("/api/v1/auth/change-username"), Map.of("newUsername", other.fullName())), active))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.businessErrorCode").value(305));
        assertEquals(displayName, users.findById(owner.getId()).orElseThrow().fullName());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), active)).andExpect(status().isOk());
    }

    @Test
    void supportMaintenanceJobsExposeCompletionThroughTheOverview() throws Exception {
        Login support = login(account("SUPPORT"));
        when(priceFeed.forceUpdateAllTrackedItems()).thenReturn(3);
        Map<String, String> reason = Map.of("reason", "Integration test maintenance");
        mvc.perform(authorized(body(post("/api/admin/system/price-update"), reason), support))
                .andExpect(status().isAccepted());
        verify(priceFeed).forceUpdateAllTrackedItems();
        mvc.perform(authorized(body(post("/api/admin/system/catalogue-sync"), reason), support))
                .andExpect(status().isAccepted());
        verify(catalogueSync).fetchAndSaveItems();
        mvc.perform(authorized(get("/api/admin/overview"), support)).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUpdate.status").value("SUCCESS"))
                .andExpect(jsonPath("$.priceUpdate.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.catalogueSync.status").value("SUCCESS"))
                .andExpect(jsonPath("$.catalogueSync.completedAt").isNotEmpty());
    }

    @Test
    void corsAllowsTheConfiguredBrowserButRejectsAnUntrustedOrigin() throws Exception {
        mvc.perform(options("/api/admin/overview").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,x-support-reason"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options("/api/admin/overview").header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredOrMissingAccessTokenStillAllowsCookieBasedSignOutOnAllDevices() throws Exception {
        User user = account("USER");
        Login first = login(user);
        Login second = login(user);
        String expired = io.jsonwebtoken.Jwts.builder()
                .subject(user.getId().toString()).claim("sid", "irrelevant")
                .issuer("workflow-test").audience().add("workflow-test-web").and()
                .claim("tokenType", "access")
                .expiration(java.util.Date.from(Instant.now().minusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
                .compact();
        mvc.perform(cookieAuth("/api/v1/auth/logout").cookie(first.refresh())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        for (Login device : List.of(first, second)) {
            mvc.perform(authorized(get("/api/v1/auth/validate-token"), device)).andExpect(status().isUnauthorized());
            mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(device.refresh())).andExpect(status().isUnauthorized());
        }
        mvc.perform(cookieAuth("/api/v1/auth/logout").cookie(first.refresh())).andExpect(status().isNoContent());
        mvc.perform(cookieAuth("/api/v1/auth/logout")).andExpect(status().isNoContent());
        Login fresh = login(user);
        // A stale cookie cannot log out a subsequent login.
        mvc.perform(cookieAuth("/api/v1/auth/logout").cookie(first.refresh())).andExpect(status().isNoContent());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), fresh)).andExpect(status().isOk());
        mvc.perform(cookieAuth("/api/v1/auth/logout").cookie(fresh.refresh())).andExpect(status().isNoContent());
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), fresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieAuthenticationRejectsSimpleRequestsAndUntrustedOriginsWithoutRevokingSession() throws Exception {
        Login device = login(account("USER"));
        for (String endpoint : List.of("/api/v1/auth/logout", "/api/v1/auth/refresh")) {
            mvc.perform(post(endpoint).cookie(device.refresh())).andExpect(status().isForbidden());
            mvc.perform(cookieAuth(endpoint).cookie(device.refresh()).header(HttpHeaders.ORIGIN, "https://evil.example"))
                    .andExpect(status().isForbidden());
            mvc.perform(cookieAuth(endpoint).cookie(device.refresh()).header(HttpHeaders.ORIGIN, "null"))
                    .andExpect(status().isForbidden());
        }
        for (String endpoint : List.of("/api/v1/auth/lo%67out", "/api/v1/auth/refre%73h")) {
            mvc.perform(post(java.net.URI.create(endpoint)).cookie(device.refresh()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(authorized(get("/api/v1/auth/validate-token"), device)).andExpect(status().isOk());
        mvc.perform(cookieAuth("/api/v1/auth/refresh").cookie(device.refresh())
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")).andExpect(status().isOk());
        mvc.perform(options("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-requested-with,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("x-requested-with")));
    }

    private MockHttpServletRequestBuilder cookieAuth(String path) {
        return post(path).header("X-Requested-With", "XMLHttpRequest");
    }

    private User account(String authority) {
        String name = "workflow-" + UUID.randomUUID().toString().substring(0, 12);
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.com")
                .password(passwords.encode(PASSWORD)).enabled(true).accountLocked(false)
                .roles(new ArrayList<>(List.of(roles.findByName(authority).orElseThrow())))
                .items(new ArrayList<>()).build());
    }

    private CsItem catalogueItem() {
        String id = UUID.randomUUID().toString();
        return catalogue.saveAndFlush(CsItem.builder().id(id).name("Case " + id)
                .marketHashName("Case " + id).image("https://example.com/item.png").build());
    }

    private Map<String, Object> portfolio(CsItem item, int quantity) {
        return Map.of("itemName", item.getName(), "csItemId", item.getId(), "itemStatus", "BOUGHT",
                "date", "12.10.2026", "quantity", quantity, "price", "2.50");
    }

    private PriceFeedSnapshot publication(CsItem item, Instant time, String version) {
        return new PriceFeedSnapshot(Map.of(item.getMarketHashName(), new BigDecimal("10.00")),
                new BigDecimal("0.90"), version + item.getId(), time, null, time.toEpochMilli());
    }

    private Map<String, Object> credentials(User user, String password) {
        return Map.of("email", user.getEmail(), "password", password, "rememberMe", true);
    }

    private Login login(User user) throws Exception {
        return login(user, PASSWORD);
    }

    private Login login(User user, String password) throws Exception {
        MvcResult result = mvc.perform(body(post("/api/v1/auth/authenticate"), credentials(user, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(1800)).andReturn();
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
        assertTrue(cookie.contains("Max-Age="));
        return readLogin(result);
    }

    private Login readLogin(MvcResult result) throws Exception {
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        String value = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        return new Login(response(result).get("token").asText(), new Cookie("refresh_token", value));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value))
                .with(servletRequest -> { servletRequest.setRemoteAddr(clientAddress); return servletRequest; });
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, Login login) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + login.access());
    }

    private JsonNode response(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Login(String access, Cookie refresh) { }
}
