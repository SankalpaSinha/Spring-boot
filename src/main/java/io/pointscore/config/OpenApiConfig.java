package io.pointscore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI pointscoreOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                .title("PointsCore API")
                .version("v1")
                .description("""
                        A loyalty points and rewards engine.

                        Members earn points on purchases according to configurable rules,
                        climb tiers, and redeem rewards. Points are held in dated lots and
                        expire twelve months after they are earned.

                        Balances are derived from an append-only ledger rather than stored
                        in a mutable column, so every balance can be explained by replaying
                        its history.
                        """)
                .license(new License().name("MIT")));
    }
}
