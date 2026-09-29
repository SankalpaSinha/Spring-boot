package io.pointscore.points;

import java.time.Instant;

public final class PointsDtos {

    private PointsDtos() {
    }

    /**
     * @param balance        spendable points now
     * @param expiringSoon   how many of those die within the warning window
     * @param nextExpiryAt   when the soonest lot expires, or null if none
     * @param lifetimeEarned every point ever earned, for display
     */
    public record BalanceResponse(
            Long memberId,
            String tierCode,
            String tierName,
            int balance,
            int expiringSoon,
            Instant nextExpiryAt,
            int lifetimeEarned
    ) {
    }

    public record LedgerEntryResponse(
            Long id,
            String type,
            int points,
            String description,
            Instant createdAt
    ) {

        public static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(),
                    entry.getEntryType().name(),
                    entry.getPoints(),
                    entry.getDescription(),
                    entry.getCreatedAt());
        }
    }
}
