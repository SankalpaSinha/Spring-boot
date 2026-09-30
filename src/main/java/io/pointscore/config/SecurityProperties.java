package io.pointscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param jwtSecret      HS256 signing key, at least 32 bytes. Blank means
 *                       "generate one at boot", which is fine for development
 *                       and tests and wrong for anything with two instances or
 *                       a restart
 * @param tokenTtl       how long an issued token stays valid
 * @param bootstrapAdmin the admin to create at startup if none exists
 */
@ConfigurationProperties(prefix = "pointscore.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("12h") Duration tokenTtl,
        BootstrapAdmin bootstrapAdmin
) {

    public SecurityProperties {
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException(
                    "pointscore.security.token-ttl must be positive, was " + tokenTtl);
        }
    }

    public record BootstrapAdmin(String email, String password) {
    }
}
