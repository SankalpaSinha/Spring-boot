package io.pointscore.auth;

import io.pointscore.auth.AuthDtos.TokenResponse;
import io.pointscore.auth.JwtService.IssuedToken;
import io.pointscore.common.BadRequestException;
import io.pointscore.common.ConflictException;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * bcrypt hashes at most 72 BYTES, and Spring Security 7 rejects longer
     * input outright. The DTO's @Size counts characters, so a 40-character
     * passphrase of non-Latin script can still be over the limit.
     */
    static final int BCRYPT_MAX_BYTES = 72;

    /**
     * A real bcrypt hash of nothing in particular. Compared against when the
     * email is unknown, so a failed login takes the same time whether or not
     * the address exists. Without it, "no such account" returns in microseconds
     * and "wrong password" in tens of milliseconds, and the difference is an
     * enumeration oracle.
     */
    private final String dummyHash;

    private final UserAccountRepository userAccountRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository userAccountRepository,
                       MemberService memberService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Enrols a member and creates their login in one transaction. If the
     * account cannot be created the enrolment rolls back with it: a member
     * nobody can log in as is a support ticket waiting to happen.
     */
    @Transactional
    public Member signUp(String name, String email, String password) {
        requireHashable(password);
        Member member = memberService.enrol(name, email);
        try {
            userAccountRepository.saveAndFlush(
                    UserAccount.memberAccount(member, email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException ex) {
            // Reachable when a staff account already uses this address. Same
            // code and wording as the member collision in MemberService, so
            // the response does not reveal which kind of account it hit.
            throw new ConflictException("EMAIL_ALREADY_ENROLLED",
                    "a member with email " + email + " is already enrolled");
        }
        return member;
    }

    /**
     * Idempotent: an admin that already exists is returned, not recreated. An
     * existing account under that address with any other role is a
     * misconfiguration and fails the boot, rather than quietly running with
     * no admin at all.
     */
    @Transactional
    public UserAccount ensureAdmin(String email, String password) {
        String address = email.trim();
        return userAccountRepository.findByEmailIgnoreCase(address)
                .map(existing -> {
                    if (existing.getRole() != UserRole.ADMIN) {
                        throw new IllegalStateException("bootstrap admin email " + address
                                + " belongs to a " + existing.getRole() + " account");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    requireHashable(password);
                    log.info("Creating admin account {}", address);
                    return userAccountRepository.save(
                            UserAccount.adminAccount(address, passwordEncoder.encode(password)));
                });
    }

    private static void requireHashable(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("PASSWORD_TOO_LONG",
                    "password must be at most " + BCRYPT_MAX_BYTES + " bytes when UTF-8 encoded");
        }
    }

    /**
     * Not transactional on purpose: bcrypt is slow by design, and holding a
     * pooled connection open for the whole comparison would let a burst of
     * logins starve everything else of the pool.
     */
    public TokenResponse login(String email, String password) {
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email.trim()).orElse(null);

        String hash = account != null ? account.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(password, hash);
        if (account == null || !matches) {
            throw new InvalidCredentialsException();
        }

        IssuedToken token = jwtService.issue(account);
        return new TokenResponse(
                token.value(), "Bearer", token.expiresAt(), account.getRole(), account.getMemberId());
    }
}
