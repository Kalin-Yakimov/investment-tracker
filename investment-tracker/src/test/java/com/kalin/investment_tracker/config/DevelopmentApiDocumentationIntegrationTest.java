package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real springdoc resources and security; never connects to the developer DB or providers. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:development-api-docs;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", "app.bootstrap-admin-email=",
        "spring.mail.username=docs@example.invalid", "spring.mail.password=synthetic-only",
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "price.update.initial-delay-ms=86400000", "price.history.prune-cron=-",
        "security.auth.cleanup-cron=-", "security.verification.cleanup-cron=-"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc(printOnlyOnFailure = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevelopmentApiDocumentationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @MockitoBean CsItemService catalogue;
    @MockitoBean PriceFeedService prices;
    @MockitoBean EmailService email;
    @MockitoBean RestTemplate outbound;

    @Test void localSwaggerRedirectHtmlJavaScriptAndStylesAreReachableWithoutLogin() throws Exception {
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui-bundle.js")));
        for (String resource : java.util.List.of("swagger-ui-bundle.js", "swagger-ui.css", "swagger-initializer.js")) {
            mvc.perform(get("/swagger-ui/" + resource)).andExpect(status().isOk());
        }
        mvc.perform(head("/swagger-ui/index.html")).andExpect(status().isOk());
        verifyNoInteractions(outbound, email);
    }

    @Test void generatedApiDescribesBearerAuthenticationAndKeepsPublicAuthOperationsPublic() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var api = json.readTree(result.getResponse().getContentAsString());
        assertEquals("http", api.at("/components/securitySchemes/bearerAuth/type").asText());
        assertEquals("bearer", api.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertTrue(api.get("security").get(0).has("bearerAuth"));
        var paths = api.get("paths");
        assertTrue(paths.has("/get-user-items"));
        assertEquals(0, paths.get("/api/v1/auth/authenticate").get("post").get("security").size());
        // Protected operations inherit the global requirement rather than opting out.
        assertFalse(paths.get("/get-user-items").get("get").has("security"));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @Test void swaggerConfigurationDoesNotPersistAuthorizationOrUseThePetstoreExample() throws Exception {
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.persistAuthorization").value(false));
    }

    @Test void remoteRequestsCannotReadDocumentationEvenWhenForwardingHeadersClaimLoopback() throws Exception {
        for (String path : java.util.List.of("/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs.yaml")) {
            mvc.perform(get(path).with(request -> { request.setRemoteAddr("192.0.2.45"); return request; })
                            .header("X-Forwarded-For", "127.0.0.1")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test void documentationDoesNotGrantApplicationAccessOrBypassCookieWriteProtection() throws Exception {
        mvc.perform(get("/get-user-items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var paths = json.readTree(result.getResponse().getContentAsString()).get("paths");
        for (String path : java.util.List.of("/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            var parameter = paths.get(path).get("post").get("parameters").get(0);
            assertEquals("X-Requested-With", parameter.get("name").asText());
            assertTrue(parameter.get("required").asBoolean());
            assertEquals("XMLHttpRequest", parameter.get("schema").get("default").asText());
        }
    }
}
