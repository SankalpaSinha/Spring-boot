package io.pointscore.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            String email,

            @NotBlank(message = "password is required")
            String password
    ) {
    }

    /**
     * @param memberId the member this token acts for; absent for staff
     */
    public record TokenResponse(
            String token,
            String tokenType,
            Instant expiresAt,
            UserRole role,
            Long memberId
    ) {
    }

    public record MeResponse(
            Long accountId,
            String email,
            UserRole role,
            Long memberId
    ) {
        public static MeResponse from(AuthPrincipal principal) {
            return new MeResponse(
                    principal.accountId(), principal.email(), principal.role(), principal.memberId());
        }
    }
}
