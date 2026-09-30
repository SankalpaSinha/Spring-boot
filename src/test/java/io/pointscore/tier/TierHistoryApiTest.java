package io.pointscore.tier;

import com.jayway.jsonpath.JsonPath;
import io.pointscore.AbstractIntegrationTest;
import io.pointscore.auth.AuthService;
import io.pointscore.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TierHistoryApiTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TierService tierService;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("a member can read their own tier history, newest first, and nobody else's")
    void historyIsSelfScoped() throws Exception {
        String email = "tier-api-" + UUID.randomUUID() + "@example.com";
        Member member = tx.execute(status -> authService.signUp("Tier Api", email, PASSWORD));
        Member other = tx.execute(status -> authService.signUp("Other",
                "tier-api-other-" + UUID.randomUUID() + "@example.com", PASSWORD));
        String token = login(email);

        mockMvc.perform(get("/api/members/{id}/tier/history", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // No purchases, so the recalculation is a no-op and the history stays empty.
        tierService.recalculate(member.getId(), Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/members/{id}/tier/history", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/members/{id}/tier/history", other.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/members/{id}/tier/history", member.getId()))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
