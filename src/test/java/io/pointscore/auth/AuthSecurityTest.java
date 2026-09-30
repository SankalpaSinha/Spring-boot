package io.pointscore.auth;

import com.jayway.jsonpath.JsonPath;
import io.pointscore.AbstractIntegrationTest;
import io.pointscore.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Milestone 5: JWT authentication and the admin/member split. */
class AuthSecurityTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
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

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "not-it"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(uniqueEmail("nobody"), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
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

        // Flip the last character of the signature.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
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
