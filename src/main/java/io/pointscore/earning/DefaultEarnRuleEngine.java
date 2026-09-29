package io.pointscore.earning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The default earning scheme.
 *
 * <h2>The algorithm</h2>
 * <ol>
 * <li><b>Discard rules that are not live</b> at {@code context.occurredAt()}.
 * Note: at the time of the <em>purchase</em>, not now. A receipt that
 * reaches us three days late must earn at the rate that applied when it
 * was rung up, or a promotion ending at midnight would rob everyone whose
 * till batched its uploads.</li>
 *
 * <li><b>Find the BASE rule</b> and use it to turn money into points:
 * {@code basePoints = floor(amount / pointsPerUnit)}.
 * With {@code pointsPerUnit = 100}, a 250 basket gives 2 points, not 2.5.
 * If there is no live BASE rule, the purchase earns nothing --
 * return {@link EarnResult#none()}.</li>
 *
 * <li><b>Collect the promotions that match</b> -- every live CATEGORY,
 * DAY_OF_WEEK or MIN_AMOUNT rule whose condition is satisfied.</li>
 *
 * <li><b>Combine them additively</b>:
 * {@code effectiveMultiplier = 1 + Σ(multiplier − 1)}.
 * <p>
 * So 2x weekend and 3x coffee together give 1 + 1 + 2 = <b>4x</b>, not
 * 6x. This matters. Multiplying promotions together compounds: four
 * modest 2x offers would quietly become 16x, and a marketer stacking
 * campaigns would hand out sixteen times the points they budgeted for.
 * Additive stacking is what real programmes use, for exactly that
 * reason.</li>
 *
 * <li><b>Apply the tier bonus and round once, at the end</b>:
 * {@code points = floor(basePoints × effectiveMultiplier × tierMultiplier)}.
 * <p>
 * Rounding once is deliberate. Rounding after each step loses a
 * fraction every time, and those losses are always downward -- across
 * millions of transactions the programme silently underpays.</li>
 * </ol>
 *
 * <p>Specified by the thirteen tests in {@code DefaultEarnRuleEngineTest},
 * which run without Spring or a database because evaluation is a pure function
 * of its inputs.
 */
@Component
public class DefaultEarnRuleEngine implements EarnRuleEngine {

    @Override
    public EarnResult evaluate(EarnContext context) {
        List<EarnRule> live = context.rules().stream()
                .filter(rule -> rule.isLiveAt(context.occurredAt()))
                .toList();

        Optional<EarnRule> base = live.stream()
                .filter(rule -> rule.getRuleType() == EarnRuleType.BASE)
                .findFirst();

        if (base.isEmpty()) {
            return EarnResult.none();
        }

        int basePoints = basePointsFor(base.get(), context.amount());

        List<EarnRule> promotions = live.stream()
                .filter(rule -> matches(rule, context))
                .toList();

        BigDecimal effectiveMultiplier = BigDecimal.ONE;
        for (EarnRule promotion : promotions) {
            effectiveMultiplier = effectiveMultiplier
                    .add(promotion.getMultiplier().subtract(BigDecimal.ONE));
        }

        int points = floorToPoints(BigDecimal.valueOf(basePoints)
                .multiply(effectiveMultiplier)
                .multiply(context.tierMultiplier()));

        List<AppliedRule> applied = new ArrayList<>();
        applied.add(AppliedRule.from(base.get()));
        promotions.forEach(promotion -> applied.add(AppliedRule.from(promotion)));

        return new EarnResult(points, basePoints, effectiveMultiplier, applied);
    }

    /**
     * Whether a single promotion applies to this purchase.
     *
     * <p>
     * BASE is handled separately by {@link #evaluate}, so it is not a
     * promotion and must return {@code false} here.
     */
    boolean matches(EarnRule rule, EarnContext context) {
        return switch (rule.getRuleType()) {

            case BASE -> false;

            case CATEGORY -> stringList(rule, "categories")
                    .contains(context.category().toUpperCase(Locale.ROOT));

            case DAY_OF_WEEK -> stringList(rule, "days")
                    .contains(context.dayOfWeek().name());

            case MIN_AMOUNT -> context.amount()
                    .compareTo(decimal(rule, "minAmount")) >= 0;

        };

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

    /**
     * A numeric condition value, or null when absent. Tolerates Integer, Double or
     * String.
     */
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

    /**
     * Case-insensitive DayOfWeek parse that tolerates junk instead of exploding.
     */
    DayOfWeek parseDay(String name) {
        try {
            return DayOfWeek.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("not a day of the week: " + name, ex);
        }
    }
}
