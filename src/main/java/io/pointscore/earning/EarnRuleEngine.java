package io.pointscore.earning;

/**
 * Decides how many points a purchase is worth.
 *
 * <p>An interface rather than a single class, so the rules can be swapped
 * wholesale -- a brand running a different scheme gets a different
 * implementation, and tests of everything downstream can hand in a stub that
 * always returns 10 points rather than setting up rule rows.
 */
public interface EarnRuleEngine {

    EarnResult evaluate(EarnContext context);
}
