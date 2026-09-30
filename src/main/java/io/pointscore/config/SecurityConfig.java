package io.pointscore.config;

import io.pointscore.auth.AuthPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Collection;
import java.util.List;

/**
 * Stateless bearer-token security.
 *
 * <p>Three kinds of caller: nobody (sign-up, login, the public catalogue, the
 * docs), a member (their own resources, and nothing else), and an admin
 * (everything). The rules live here, in one place and in order, rather than
 * as annotations scattered over controllers where a missed one is a hole
 * nobody sees. Member-scoped URLs all sit under {@code /api/members/{memberId}}
 * precisely so that one rule can cover them.
 *
 * <p>CSRF is disabled because there are no cookies and no browser-submitted
 * forms: the token travels in a header the browser will not attach on its
 * own, so there is no ambient credential for an attacker to ride on. That
 * reasoning stops being true the moment session cookies appear.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder jwtDecoder,
                                            SecurityErrorHandlers errorHandlers) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rewards/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Purchases arrive from the brand's tills, never from
                        // the member: letting members award themselves points
                        // would be the shortest fraud in the world.
                        .requestMatchers(HttpMethod.POST, "/api/members/{memberId}/transactions").hasRole("ADMIN")
                        .requestMatchers("/api/members/{memberId}/**").access(selfOrAdmin())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorHandlers))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .build();
    }

    /**
     * The {@code role} claim becomes {@code ROLE_ADMIN} or {@code ROLE_MEMBER},
     * which is what {@code hasRole} looks for.
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authoritiesOf);
        return converter;
    }

    private static Collection<GrantedAuthority> authoritiesOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /**
     * Admins may act for any member; a member only for the {@code memberId} in
     * the path. An anonymous caller is refused here too, and Spring turns that
     * refusal into a 401 rather than a 403 because there was nobody to deny.
     */
    private static AuthorizationManager<RequestAuthorizationContext> selfOrAdmin() {
        return (authentication, context) -> {
            String target = context.getVariables().get("memberId");
            boolean allowed = AuthPrincipal.from(authentication.get())
                    .map(principal -> target != null && principal.canActFor(parseOrNull(target)))
                    .orElse(false);
            return new AuthorizationDecision(allowed);
        };
    }

    private static Long parseOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
