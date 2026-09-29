package io.pointscore.earning;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a purchase was worth, and the working that produced it.
 *
 * @param points           final points awarded, already rounded to a whole number
 * @param basePoints       what the BASE rule produced, before any multipliers
 * @param effectiveMultiplier  the combined promotion multiplier that was applied
 * @param appliedRules     every rule that fired, in the order it was considered
 */
public record EarnResult(
        int points,
        int basePoints,
        BigDecimal effectiveMultiplier,
        List<AppliedRule> appliedRules
) {

    public static EarnResult none() {
        return new EarnResult(0, 0, BigDecimal.ONE, List.of());
    }
}
