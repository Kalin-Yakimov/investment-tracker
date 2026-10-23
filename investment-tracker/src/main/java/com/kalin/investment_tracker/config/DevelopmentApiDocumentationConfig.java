package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.security.SecurityConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

/** Documentation only: these declarations grant no API permissions. */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class DevelopmentApiDocumentationConfig {
    @Bean OpenAPI developmentOpenApi() {
        return new OpenAPI().info(new Info().title("Investment Tracker API").version("v1")
                        .description("Local development only. Try it out uses the connected database and can change data. "
                                + "Use a disposable test account. Authenticate, then paste only the access token into Authorize. "
                                + "Authorization is not persisted across reloads."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean OpenApiCustomizer developmentAuthenticationDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean cookieWrite = method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                    && Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout").contains(path);
            if (SecurityConfig.PUBLIC_AUTH_PATHS.contains(path) || cookieWrite) operation.setSecurity(List.of());
            if (cookieWrite) {
                operation.addParametersItem(new Parameter().name("X-Requested-With").in("header").required(true)
                        .description("Required non-simple request header; do not disable the cookie-authentication safeguard.")
                        .schema(new StringSchema()._default("XMLHttpRequest")._enum(List.of("XMLHttpRequest"))));
            }
        }));
    }
}
