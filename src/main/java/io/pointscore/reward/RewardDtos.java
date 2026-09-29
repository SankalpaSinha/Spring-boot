package io.pointscore.reward;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RewardDtos {

    private RewardDtos() {
    }

    public record RewardResponse(
            Long id,
            String code,
            String name,
            String description,
            int costPoints,
            int stock,
            boolean inStock,
            boolean active
    ) {

        public static RewardResponse from(Reward reward) {
            return new RewardResponse(
                    reward.getId(),
                    reward.getCode(),
                    reward.getName(),
                    reward.getDescription(),
                    reward.getCostPoints(),
                    reward.getStock(),
                    reward.isInStock(),
                    reward.isActive());
        }
    }

    public record CreateRewardRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @Min(value = 1, message = "costPoints must be at least 1") Integer costPoints,
            @NotNull @Min(value = 0, message = "stock cannot be negative") Integer stock
    ) {
    }

    public record UpdateRewardRequest(
            @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Min(1) Integer costPoints,
            @Min(0) Integer stock,
            Boolean active
    ) {
    }
}
