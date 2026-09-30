package io.pointscore.auth;

import io.pointscore.config.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Issues tokens. Validation is Spring's job (see {@code SecurityConfig}); this
 * class only has to agree with it about the algorithm and the claim names.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issue(UserAccount account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.tokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(account.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AuthPrincipal.CLAIM_EMAIL, account.getEmail())
                .claim(AuthPrincipal.CLAIM_ROLE, account.getRole().name());
        if (account.getMemberId() != null) {
            claims.claim(AuthPrincipal.CLAIM_MEMBER_ID, account.getMemberId());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
