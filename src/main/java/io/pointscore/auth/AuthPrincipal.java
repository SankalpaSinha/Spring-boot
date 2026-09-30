package io.pointscore.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * What a validated token says about the caller. The one place that knows the
 * claim names, so controllers and authorisation rules never read a JWT directly.
 *
 * @param memberId the member the caller acts for; absent for staff
 */
public record AuthPrincipal(Long accountId, String email, UserRole role, Long memberId) {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_MEMBER_ID = "memberId";

    public static Optional<AuthPrincipal> from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        Object memberId = jwt.getClaim(CLAIM_MEMBER_ID);
        return Optional.of(new AuthPrincipal(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_EMAIL),
                UserRole.valueOf(jwt.getClaimAsString(CLAIM_ROLE)),
                memberId instanceof Number number ? number.longValue() : null));
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** Admins act for anyone; a member only for themselves. */
    public boolean canActFor(Long targetMemberId) {
        return isAdmin() || (memberId != null && memberId.equals(targetMemberId));
    }
}
