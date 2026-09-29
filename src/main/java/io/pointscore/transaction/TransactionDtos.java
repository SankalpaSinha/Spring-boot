package io.pointscore.transaction;

import io.pointscore.earning.AppliedRule;
import io.pointscore.earning.EarnResult;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /**
     * A purchase arriving from a till.
     *
     * @param externalRef the till's receipt number; resending the same one is
     *                    treated as a retry, not a second purchase
     * @param occurredAt  optional -- defaults to now if the till does not say
     */
    public record IngestPurchaseRequest(
            @NotBlank(message = "externalRef is required")
            @Size(max = 100, message = "externalRef must be at most 100 characters")
            String externalRef,

            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be greater than zero")
            BigDecimal amount,

            @NotBlank(message = "category is required")
            @Size(max = 60, message = "category must be at most 60 characters")
            String category,

            Instant occurredAt
    ) {
    }

    public record AppliedRuleResponse(String code, String name, BigDecimal multiplier) {

        static AppliedRuleResponse from(AppliedRule rule) {
            return new AppliedRuleResponse(rule.code(), rule.name(), rule.multiplier());
        }
    }

    /**
     * The receipt for an ingested purchase. Carries the working, not just the
     * total, so a member asking "why only 40 points?" can be answered from the
     * response rather than from the logs.
     */
    public record TransactionResponse(
            Long id,
            Long memberId,
            String externalRef,
            BigDecimal amount,
            String category,
            Instant occurredAt,
            int pointsAwarded,
            int basePoints,
            BigDecimal effectiveMultiplier,
            List<AppliedRuleResponse> appliedRules,
            boolean duplicate
    ) {

        public static TransactionResponse of(Transaction transaction, EarnResult result, boolean duplicate) {
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getMember().getId(),
                    transaction.getExternalRef(),
                    transaction.getAmount(),
                    transaction.getCategory(),
                    transaction.getOccurredAt(),
                    transaction.getPointsAwarded(),
                    result == null ? transaction.getPointsAwarded() : result.basePoints(),
                    result == null ? null : result.effectiveMultiplier(),
                    result == null ? List.of()
                            : result.appliedRules().stream().map(AppliedRuleResponse::from).toList(),
                    duplicate);
        }
    }
}
