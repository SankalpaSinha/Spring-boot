package io.pointscore.auth;

import io.pointscore.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first admin from configuration, because there is no other way
 * in: admins are not enrolled through the public API, and a migration cannot
 * read a password from the environment.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthService authService;
    private final SecurityProperties properties;

    public AdminBootstrap(AuthService authService, SecurityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.info("No bootstrap admin configured (ADMIN_EMAIL / ADMIN_PASSWORD); skipping");
            return;
        }
        authService.ensureAdmin(admin.email(), admin.password());
    }
}
