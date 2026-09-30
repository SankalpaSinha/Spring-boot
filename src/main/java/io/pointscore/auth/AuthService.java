package io.pointscore.auth;

import io.pointscore.auth.AuthDtos.TokenResponse;
import io.pointscore.auth.JwtService.IssuedToken;
import io.pointscore.common.ConflictException;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
        Member member = memberService.enrol(name, email);
        try {
            userAccountRepository.saveAndFlush(
                    UserAccount.memberAccount(member, email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException ex) {
            // Reachable when a staff account already uses this address; the
            // member-email collision is caught earlier by MemberService.
            throw new ConflictException("EMAIL_ALREADY_REGISTERED",
                    "an account with email " + email + " already exists");
        }
        return member;
    }

    /** Idempotent: an admin that already exists is returned, not recreated. */
    @Transactional
    public UserAccount ensureAdmin(String email, String password) {
        return userAccountRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    log.info("Creating admin account {}", email);
                    return userAccountRepository.save(
                            UserAccount.adminAccount(email, passwordEncoder.encode(password)));
                });
    }

    @Transactional(readOnly = true)
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
