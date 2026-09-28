package io.pointscore.earning;

/**
 * The kinds of rule the engine knows how to evaluate.
 *
 * <p>Adding a value here is a code change, but adding a <em>rule</em> is only a
 * row in {@code earn_rules}. That is the split a loyalty platform needs: a
 * marketer can launch "triple points on pastries" without a deploy, while the
 * shapes of condition they can express stay reviewed and tested.
 */
public enum EarnRuleType {

    /** Converts money to points. {@code {"pointsPerUnit": 100}} = 1 point per 100 spent. */
    BASE,

    /** Matches the purchase category. {@code {"categories": ["COFFEE"]}} */
    CATEGORY,

    /** Matches the day the purchase happened. {@code {"days": ["SATURDAY","SUNDAY"]}} */
    DAY_OF_WEEK,

    /** Matches baskets at or above a threshold. {@code {"minAmount": 2000}} */
    MIN_AMOUNT
}
