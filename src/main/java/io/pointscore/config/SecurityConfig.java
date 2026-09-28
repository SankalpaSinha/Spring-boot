package io.pointscore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Interim security: everything is open.
 *
 * <p>Spring Security on the classpath locks every endpoint behind a generated
 * password by default, which would block the whole API before there is any
 * notion of a user. Milestone 4 replaces this with JWT authentication and the
 * admin/member role split; until then this is a deliberate, temporary hole and
 * the service must not be exposed publicly.
 *
 * <p>CSRF is disabled because this is a stateless token API with no cookies and
 * no browser-submitted forms -- there is no ambient credential for an attacker
 * to ride on. That reasoning stops being true the moment session cookies appear.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TODO(milestone-4): require authentication, and restrict
                // /api/admin/** to ROLE_ADMIN.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
