package io.pointscore.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request and response shapes for the member API.
 *
 * <p>Entities are deliberately never returned directly. Serialising a JPA
 * entity leaks the schema to clients, drags lazy associations into the
 * response, and makes every column rename a breaking API change.
 */
public final class MemberDtos {

    private MemberDtos() {
    }

    public record CreateMemberRequest(
            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            @Size(max = 255, message = "email must be at most 255 characters")
            String email
    ) {
    }

    public record MemberResponse(
            Long id,
            String name,
            String email,
            String tierCode,
            String tierName,
            Instant enrolledAt
    ) {
        public static MemberResponse from(Member member) {
            return new MemberResponse(
                    member.getId(),
                    member.getName(),
                    member.getEmail(),
                    member.getTier().getCode(),
                    member.getTier().getName(),
                    member.getEnrolledAt());
        }
    }
}
