package io.pointscore.redemption;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class RedemptionDtos {

    private RedemptionDtos() {
    }

    public record RedeemRequest(
            @NotNull(message = "rewardId is required") Long rewardId
    ) {
    }

    public record RedemptionResponse(
            Long id,
            Long memberId,
            Long rewardId,
            String rewardName,
            int pointsSpent,
            int balanceAfter,
            String status,
            Instant createdAt,
            boolean duplicate
    ) {

        public static RedemptionResponse of(Redemption redemption, int balanceAfter, boolean duplicate) {
            return new RedemptionResponse(
                    redemption.getId(),
                    redemption.getMember().getId(),
                    redemption.getReward().getId(),
                    redemption.getReward().getName(),
                    redemption.getPointsSpent(),
                    balanceAfter,
                    redemption.getStatus().name(),
                    redemption.getCreatedAt(),
                    duplicate);
        }
    }
}
