package io.pointscore.tier;

import java.time.Instant;

public final class TierDtos {

    private TierDtos() {
    }

    /**
     * One movement between tiers, oldest reason first in the enum but newest
     * first in the history. {@code fromTierCode} is null on an INITIAL row.
     */
    public record TierChangeResponse(
            Long id,
            String fromTierCode,
            String toTierCode,
            TierChange.Reason reason,
            int qualifyingPoints,
            Instant changedAt
    ) {
        public static TierChangeResponse from(TierChange change) {
            return new TierChangeResponse(
                    change.getId(),
                    change.getFromTier() == null ? null : change.getFromTier().getCode(),
                    change.getToTier().getCode(),
                    change.getReason(),
                    change.getQualifyingPoints(),
                    change.getChangedAt());
        }
    }
}
