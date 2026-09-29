package io.pointscore.earning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The default earning scheme.
 *
 * <h2>The algorithm</h2>
 * <ol>
 *   <li><b>Discard rules that are not live</b> at {@code context.occurredAt()}.
 *       Note: at the time of the <em>purchase</em>, not now. A receipt that
 *       reaches us three days late must earn at the rate that applied when it
 *       was rung up, or a promotion ending at midnight would rob everyone whose
 *       till batched its uploads.</li>
 *
 *   <li><b>Find the BASE rule</b> and use it to turn money into points:
 *       {@code basePoints = floor(amount / pointsPerUnit)}.
 *       With {@code pointsPerUnit = 100}, a 250 basket gives 2 points, not 2.5.
 *       If there is no live BASE rule, the purchase earns nothing --
 *       return {@link EarnResult#none()}.</li>
 *
 *   <li><b>Collect the promotions that match</b> -- every live CATEGORY,
 *       DAY_OF_WEEK or MIN_AMOUNT rule whose condition is satisfied.</li>
 *
 *   <li><b>Combine them additively</b>:
 *       {@code effectiveMultiplier = 1 + Σ(multiplier − 1)}.
 *       <p>So 2x weekend and 3x coffee together give 1 + 1 + 2 = <b>4x</b>, not
 *       6x. This matters. Multiplying promotions together compounds: four
 *       modest 2x offers would quietly become 16x, and a marketer stacking
 *       campaigns would hand out sixteen times the points they budgeted for.
 *       Additive stacking is what real programmes use, for exactly that
 *       reason.</li>
 *
 *   <li><b>Apply the tier bonus and round once, at the end</b>:
 *       {@code points = floor(basePoints × effectiveMultiplier × tierMultiplier)}.
 *       <p>Rounding once is deliberate. Rounding after each step loses a
 *       fraction every time, and those losses are always downward -- across
 *       millions of transactions the programme silently underpays.</li>
 * </ol>
 *
 * <h2>What you need to write</h2>
 * Search this file for {@code YOUR TASK}. The helpers below the stubs are
 * already done -- pulling values out of JSON is plumbing, not the interesting
 * part. Seven tests in {@code DefaultEarnRuleEngineTest} describe the expected
 * behaviour; remove the {@code @Disabled} annotations and make them pass.
 */
@Component
public class DefaultEarnRuleEngine implements EarnRuleEngine {

    @Override
    public EarnResult evaluate(EarnContext context) {
        // ------------------------------------------------------------------
        // YOUR TASK (milestone 2, part 1)
        //
        // Implement the five steps in the class javadoc above.
        //
        // A sketch, if you want one:
        //
        //   1. Filter context.rules() down to rules that are live at
        //      context.occurredAt()  ->  EarnRule.isLiveAt(Instant) exists.
        //
        //   2. Find the rule whose ruleType is BASE. If there is none,
        //      return EarnResult.none().
        //      Compute basePoints with basePointsFor(...) below.
        //
        //   3. Walk the remaining live rules, keeping the ones for which
        //      matches(rule, context) is true. Keep them in priority order --
        //      the list arrives sorted, so preserve it.
        //
        //   4. effectiveMultiplier = ONE plus the sum of (multiplier - ONE)
        //      over the matching promotions.
        //
        //   5. Multiply basePoints by effectiveMultiplier and by
        //      context.tierMultiplier(), then floor the result to an int.
        //      Build the applied-rules list: the BASE rule first, then the
        //      promotions that fired, and return an EarnResult.
        //
        // Things the tests will check that are easy to miss:
        //   - a purchase too small to earn anything yields 0 points, but the
        //     BASE rule still counts as applied;
        //   - day-of-week uses context.dayOfWeek(), never occurredAt directly;
        //   - an inactive or out-of-window rule must not fire.
        // ------------------------------------------------------------------
        throw new UnsupportedOperationException(
                "milestone 2: implement DefaultEarnRuleEngine.evaluate -- see the javadoc above");
    }

    /**
     * Whether a single promotion applies to this purchase.
     *
     * <p>BASE is handled separately by {@link #evaluate}, so it is not a
     * promotion and must return {@code false} here.
     */
    boolean matches(EarnRule rule, EarnContext context) {
        // ------------------------------------------------------------------
        // YOUR TASK (milestone 2, part 2)
        //
        // Switch on rule.getRuleType() and decide whether it fires:
        //
        //   BASE         -> false (not a promotion)
        //
        //   CATEGORY     -> conditions {"categories": ["COFFEE", "PASTRY"]}
        //                   fires when context.category() is in that list.
        //                   Compare case-insensitively: a till sending
        //                   "coffee" must match a rule written "COFFEE".
        //                   Use stringList(rule, "categories").
        //
        //   DAY_OF_WEEK  -> conditions {"days": ["SATURDAY", "SUNDAY"]}
        //                   fires when context.dayOfWeek() is named in the
        //                   list. DayOfWeek.valueOf(..) parses the names, but
        //                   mind the case. Use stringList(rule, "days").
        //
        //   MIN_AMOUNT   -> conditions {"minAmount": 2000}
        //                   fires when the basket is at or above the
        //                   threshold -- inclusive, so a basket of exactly
        //                   2000 qualifies. Use decimal(rule, "minAmount").
        //                   Remember BigDecimal comparison is compareTo, not
        //                   equals: new BigDecimal("2000.00").equals(
        //                   new BigDecimal("2000")) is false.
        // ------------------------------------------------------------------
        throw new UnsupportedOperationException(
                "milestone 2: implement DefaultEarnRuleEngine.matches");
    }

    // ---------------------------------------------------------------------
    // Helpers below are already written. Read them, but you do not need to
    // change them.
    // ---------------------------------------------------------------------

    /**
     * Money to points, rounding down: {@code floor(amount / pointsPerUnit)}.
     * A missing or non-positive {@code pointsPerUnit} would mean a misconfigured
     * rule, so it fails loudly rather than dividing by zero.
     */
    int basePointsFor(EarnRule baseRule, BigDecimal amount) {
        BigDecimal pointsPerUnit = decimal(baseRule, "pointsPerUnit");
        if (pointsPerUnit == null || pointsPerUnit.signum() <= 0) {
            throw new IllegalStateException(
                    "rule " + baseRule.getCode() + " is BASE but has no positive pointsPerUnit");
        }
        return amount.divideToIntegralValue(pointsPerUnit).intValue();
    }

    /** Rounds a points total down to a whole number. */
    int floorToPoints(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR).intValue();
    }

    /** A numeric condition value, or null when absent. Tolerates Integer, Double or String. */
    BigDecimal decimal(EarnRule rule, String key) {
        Object raw = conditions(rule).get(key);
        return switch (raw) {
            case null -> null;
            case BigDecimal bigDecimal -> bigDecimal;
            case Number number -> new BigDecimal(number.toString());
            case String string -> new BigDecimal(string.trim());
            default -> throw new IllegalStateException(
                    "rule " + rule.getCode() + " has a non-numeric " + key + ": " + raw);
        };
    }

    /** A list-of-strings condition value, upper-cased; empty when absent. */
    List<String> stringList(EarnRule rule, String key) {
        Object raw = conditions(rule).get(key);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException(
                    "rule " + rule.getCode() + " has a non-list " + key + ": " + raw);
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object element : list) {
            values.add(String.valueOf(element).trim().toUpperCase(Locale.ROOT));
        }
        return values;
    }

    /** Never-null view of a rule's conditions. */
    private Map<String, Object> conditions(EarnRule rule) {
        return rule.getConditions() == null ? Map.of() : rule.getConditions();
    }

    /** Case-insensitive DayOfWeek parse that tolerates junk instead of exploding. */
    DayOfWeek parseDay(String name) {
        try {
            return DayOfWeek.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("not a day of the week: " + name, ex);
        }
    }
}
