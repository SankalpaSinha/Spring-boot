package io.pointscore.auth;

import com.jayway.jsonpath.JsonPath;
import io.pointscore.AbstractIntegrationTest;
import io.pointscore.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Milestone 5: JWT authentication and the admin/member split. */
class AuthSecurityTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("a request with no token is refused with a 401 problem response")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/members/1/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/admin/rewards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("sign-up creates a login, and the token says who you are")
    void signUpThenLogin() throws Exception {
        String email = uniqueEmail("signup");

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberId").isNumber());
    }

    @Test
    @DisplayName("sign-up without a password is a validation error, not a passwordless account")
    void signUpRequiresPassword() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "%s"}
                                """.formatted(uniqueEmail("nopass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("a wrong password and an unknown email get the same answer")
    void badCredentialsAreRefused() throws Exception {
        String email = uniqueEmail("wrongpw");
        signUp(email);

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "not-it"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(uniqueEmail("nobody"), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // Byte-for-byte the same apart from the timestamp, so nothing in the
        // body says which of the two happened.
        assertThat(withoutTimestamp(wrongPassword)).isEqualTo(withoutTimestamp(unknownEmail));
    }

    @Test
    @DisplayName("a password over bcrypt's 72-byte limit is a 400, not a 500")
    void overlongPasswordIsRejectedCleanly() throws Exception {
        // 40 characters of Devanagari: within @Size(max = 72) characters, but
        // 120 bytes in UTF-8, which bcrypt will not hash.
        String password = "\u0928\u092e\u0938\u094d\u0924\u0947".repeat(7);

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "%s", "password": "%s"}
                                """.formatted(uniqueEmail("longpw"), password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    @DisplayName("a member sees their own resources and nobody else's")
    void memberIsConfinedToThemselves() throws Exception {
        String emailA = uniqueEmail("a");
        Member a = signUp(emailA);
        Member b = signUp(uniqueEmail("b"));
        String tokenA = login(emailA, PASSWORD);

        mockMvc.perform(get("/api/members/{id}/balance", a.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/{id}", a.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/members/{id}/balance", b.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/members/{id}", b.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("members cannot award themselves points; the till is an admin")
    void onlyAdminsIngestPurchases() throws Exception {
        String email = uniqueEmail("till");
        Member member = signUp(email);
        String memberToken = login(email, PASSWORD);
        String adminToken = adminToken();

        String purchase = """
                {"externalRef": "%s", "amount": 500.00, "category": "COFFEE", "occurredAt": "2026-09-30T10:00:00Z"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/members/{id}/transactions", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchase))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/members/{id}/transactions", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchase))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the admin tree is closed to members and open to admins, who may also act for anyone")
    void adminSplit() throws Exception {
        String email = uniqueEmail("member");
        Member member = signUp(email);
        String memberToken = login(email, PASSWORD);
        String adminToken = adminToken();

        mockMvc.perform(get("/api/admin/rewards").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Mutation too, not only reads: the rule is on the path, not the verb.
        mockMvc.perform(post("/api/admin/rewards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "HACK", "name": "Free everything", "costPoints": 1, "stock": 1}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/rewards").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/members/{id}/balance", member.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token whose signature does not match is refused")
    void tamperedTokenIsRefused() throws Exception {
        String email = uniqueEmail("tamper");
        signUp(email);
        String token = login(email, PASSWORD);

        // Flip the FIRST character of the signature. The last one only carries
        // two or four significant bits, so flipping it can decode to the very
        // same bytes and leave the token valid.
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart) + (first == 'A' ? 'B' : 'A') + token.substring(sigStart + 1);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("an expired token is refused even though its signature is good")
    void expiredTokenIsRefused() throws Exception {
        String email = uniqueEmail("expired");
        Member member = signUp(email);

        // Signed with the real key, expired well past the decoder's clock skew.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .claim("email", email)
                .claim("role", "MEMBER")
                .claim("memberId", member.getId())
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/members/{id}/balance", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("the catalogue and the docs stay public")
    void publicSurfaceStaysPublic() throws Exception {
        mockMvc.perform(get("/api/rewards")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- helpers

    private static String withoutTimestamp(String problemJson) {
        return problemJson.replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private Member signUp(String email) {
        return tx.execute(status -> authService.signUp("Auth Test", email, PASSWORD));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** Admins are not enrolled through the API, so the test makes one directly. */
    private String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        tx.executeWithoutResult(status -> authService.ensureAdmin(email, PASSWORD));
        return login(email, PASSWORD);
    }
}
