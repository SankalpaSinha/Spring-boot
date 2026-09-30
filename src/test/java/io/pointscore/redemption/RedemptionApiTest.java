package io.pointscore.redemption;

import com.jayway.jsonpath.JsonPath;
import io.pointscore.AbstractIntegrationTest;
import io.pointscore.auth.AuthService;
import io.pointscore.member.Member;
import io.pointscore.transaction.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redemption over HTTP. The service tests prove the locking and idempotency;
 * this proves the response can actually be rendered on every path, which
 * a service-level test cannot see.
 */
class RedemptionApiTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("a retried redemption replays the original as 200, and the balance is charged once")
    void retryReplaysTheOriginal() throws Exception {
        String email = "redeem-api-" + UUID.randomUUID() + "@example.com";
        Member member = tx.execute(status -> authService.signUp("Redeem Api", email, PASSWORD));
        tx.executeWithoutResult(status -> transactionService.ingest(
                member.getId(), "API-" + UUID.randomUUID(), new BigDecimal("100000.00"),
                "GENERAL", Instant.parse("2026-09-30T06:00:00Z")));
        String token = login(email);
        String key = "retry-" + UUID.randomUUID();

        // Reward 1 is the seeded FREE_LATTE.
        String first = mockMvc.perform(post("/api/members/{id}/redemptions", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/members/{id}/redemptions", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.id").value(JsonPath.<Integer>read(first, "$.id")))
                .andExpect(jsonPath("$.rewardName").value(JsonPath.<String>read(first, "$.rewardName")))
                .andExpect(jsonPath("$.balanceAfter").value(JsonPath.<Integer>read(first, "$.balanceAfter")));

        mockMvc.perform(get("/api/members/{id}/redemptions", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
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
