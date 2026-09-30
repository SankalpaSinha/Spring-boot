package io.pointscore.auth;

import io.pointscore.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A login. Holds a password hash and a role, and for members a pointer to the
 * member it acts for. See V4 for why this is not a column on members.
 */
@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * Held as a plain id rather than a lazy association. The only thing the
     * authentication path needs is the number to put in the token, and a proxy
     * that must not be touched outside a transaction is a trap for no gain.
     */
    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public static UserAccount memberAccount(Member member, String email, String passwordHash) {
        UserAccount account = new UserAccount();
        account.email = email.trim();
        account.passwordHash = passwordHash;
        account.role = UserRole.MEMBER;
        account.memberId = member.getId();
        return account;
    }

    public static UserAccount adminAccount(String email, String passwordHash) {
        UserAccount account = new UserAccount();
        account.email = email.trim();
        account.passwordHash = passwordHash;
        account.role = UserRole.ADMIN;
        return account;
    }
}
