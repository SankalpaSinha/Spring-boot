package io.pointscore.earning;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 2, specified as tests.
 *
 * <p><b>To begin: delete the {@code @Disabled} line just below.</b> All of these
 * will then fail, because {@code evaluate} still throws. Work down them one at a
 * time until they are green.
 *
 * <p>No Spring, no database, no mocks -- the engine is a pure function, so its
 * tests are plain objects and arithmetic. If you find yourself wanting a
 * repository here, the design has gone wrong.
 */
@Disabled("milestone 2: delete this line to start")
class DefaultEarnRuleEngineTest {

    private final DefaultEarnRuleEngine engine = new DefaultEarnRuleEngine();

    /** The brand is in Hyderabad. This is not decoration -- one test below turns on it. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** 2026-01-03 was a Saturday. 04:00 IST that day is 22:30 on Friday in UTC. */
    private static final Instant SATURDAY_EARLY_IST =
            LocalDate.of(2026, 1, 3).atTime(4, 0).atZone(IST).toInstant();

    /** 2026-01-07 was a Wednesday. */
    private static final Instant MIDWEEK =
            LocalDate.of(2026, 1, 7).atTime(12, 0).atZone(IST).toInstant();

    @Nested
    @DisplayName("the base rule")
    class BaseRule {

        @Test
        @DisplayName("awards one point per hundred spent")
        void awardsOnePointPerHundred() {
            EarnResult result = engine.evaluate(
                    context("500", "COFFEE", MIDWEEK, "1.00", baseRule()));

            assertThat(result.points()).isEqualTo(5);
            assertThat(result.basePoints()).isEqualTo(5);
            assertThat(codes(result)).containsExactly("BASE_EARN");
        }

        @Test
        @DisplayName("rounds partial units down, so 250 spent is 2 points and not 2.5")
        void roundsPartialUnitsDown() {
            EarnResult result = engine.evaluate(
                    context("250", "COFFEE", MIDWEEK, "1.00", baseRule()));

            assertThat(result.points()).isEqualTo(2);
        }

        @Test
        @DisplayName("earns nothing at all when no base rule is live")
        void earnsNothingWithoutABaseRule() {
            EarnRule weekendOnly = rule("WEEKEND_2X", EarnRuleType.DAY_OF_WEEK, "2.00",
                    Map.of("days", List.of("SATURDAY", "SUNDAY")));

            EarnResult result = engine.evaluate(
                    context("5000", "COFFEE", SATURDAY_EARLY_IST, "1.50", weekendOnly));

            // A promotion with nothing to multiply must not invent points.
            assertThat(result.points()).isZero();
            assertThat(result.appliedRules()).isEmpty();
        }

        @Test
        @DisplayName("a purchase too small to earn yields zero, but the base rule still counted")
        void smallPurchaseEarnsZeroButBaseStillApplies() {
            EarnResult result = engine.evaluate(
                    context("50", "COFFEE", MIDWEEK, "1.00", baseRule()));

            assertThat(result.points()).isZero();
            // The rule did fire -- it simply produced nothing. Reporting it as
            // "no rules applied" would make the API unable to explain the zero.
            assertThat(codes(result)).containsExactly("BASE_EARN");
        }
    }

    @Nested
    @DisplayName("promotion matching")
    class Matching {

        @Test
        @DisplayName("category matching ignores case, because tills are inconsistent")
        void categoryMatchingIgnoresCase() {
            EarnRule coffee = rule("COFFEE_3X", EarnRuleType.CATEGORY, "3.00",
                    Map.of("categories", List.of("COFFEE")));

            EarnResult result = engine.evaluate(
                    context("1000", "coffee", MIDWEEK, "1.00", baseRule(), coffee));

            assertThat(codes(result)).containsExactly("BASE_EARN", "COFFEE_3X");
            assertThat(result.points()).isEqualTo(30);
        }

        @Test
        @DisplayName("a non-matching category leaves the purchase at base rate")
        void nonMatchingCategoryDoesNotFire() {
            EarnRule coffee = rule("COFFEE_3X", EarnRuleType.CATEGORY, "3.00",
                    Map.of("categories", List.of("COFFEE")));

            EarnResult result = engine.evaluate(
                    context("1000", "SANDWICH", MIDWEEK, "1.00", baseRule(), coffee));

            assertThat(result.points()).isEqualTo(10);
            assertThat(codes(result)).containsExactly("BASE_EARN");
        }

        @Test
        @DisplayName("weekend promotions use the brand's timezone, not UTC")
        void weekendUsesProgrammeTimezone() {
            // The trap: this instant is Saturday in Hyderabad but still Friday
            // in UTC. An engine that called occurredAt.atZone(UTC) would refuse
            // to pay the weekend bonus for the first 5.5 hours of every
            // Saturday -- a bug invisible in testing and infuriating in
            // production. Guard the assumption explicitly:
            assertThat(SATURDAY_EARLY_IST.atZone(ZoneOffset.UTC).getDayOfWeek())
                    .isEqualTo(DayOfWeek.FRIDAY);
            assertThat(SATURDAY_EARLY_IST.atZone(IST).getDayOfWeek())
                    .isEqualTo(DayOfWeek.SATURDAY);

            EarnRule weekend = rule("WEEKEND_2X", EarnRuleType.DAY_OF_WEEK, "2.00",
                    Map.of("days", List.of("SATURDAY", "SUNDAY")));

            EarnResult result = engine.evaluate(
                    context("1000", "COFFEE", SATURDAY_EARLY_IST, "1.00", baseRule(), weekend));

            assertThat(result.points()).isEqualTo(20);
            assertThat(codes(result)).containsExactly("BASE_EARN", "WEEKEND_2X");
        }

        @Test
        @DisplayName("the minimum-amount threshold is inclusive")
        void minAmountIsInclusive() {
            EarnRule bigBasket = rule("BIG_BASKET_1_5X", EarnRuleType.MIN_AMOUNT, "1.50",
                    Map.of("minAmount", 2000));

            // Exactly on the threshold must qualify. Using > instead of >= here
            // is the single most common off-by-one in promotion code.
            EarnResult onThreshold = engine.evaluate(
                    context("2000", "GROCERY", MIDWEEK, "1.00", baseRule(), bigBasket));
            assertThat(codes(onThreshold)).contains("BIG_BASKET_1_5X");
            assertThat(onThreshold.points()).isEqualTo(30);

            EarnResult justUnder = engine.evaluate(
                    context("1999.99", "GROCERY", MIDWEEK, "1.00", baseRule(), bigBasket));
            assertThat(codes(justUnder)).containsExactly("BASE_EARN");
        }

        @Test
        @DisplayName("an inactive rule never fires")
        void inactiveRuleDoesNotFire() {
            EarnRule retired = rule("COFFEE_3X", EarnRuleType.CATEGORY, "3.00",
                    Map.of("categories", List.of("COFFEE")));
            retired.setActive(false);

            EarnResult result = engine.evaluate(
                    context("1000", "COFFEE", MIDWEEK, "1.00", baseRule(), retired));

            assertThat(result.points()).isEqualTo(10);
            assertThat(codes(result)).containsExactly("BASE_EARN");
        }

        @Test
        @DisplayName("a rule is judged against the purchase time, not the present moment")
        void validityWindowIsCheckedAgainstThePurchaseTime() {
            EarnRule newYearOnly = rule("NEW_YEAR_2X", EarnRuleType.CATEGORY, "2.00",
                    Map.of("categories", List.of("COFFEE")));
            // Ran for the first two days of January only.
            newYearOnly.setValidFrom(LocalDate.of(2026, 1, 1).atStartOfDay(IST).toInstant());
            newYearOnly.setValidTo(LocalDate.of(2026, 1, 3).atStartOfDay(IST).toInstant());

            // Bought on the 7th: the promotion had ended, so base rate only.
            EarnResult afterItEnded = engine.evaluate(
                    context("1000", "COFFEE", MIDWEEK, "1.00", baseRule(), newYearOnly));
            assertThat(afterItEnded.points()).isEqualTo(10);

            // Bought on the 2nd, even if the receipt reaches us much later, the
            // promotion still pays -- the customer earned it at the till.
            Instant duringPromotion = LocalDate.of(2026, 1, 2).atTime(10, 0).atZone(IST).toInstant();
            EarnResult during = engine.evaluate(
                    context("1000", "COFFEE", duringPromotion, "1.00", baseRule(), newYearOnly));
            assertThat(during.points()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("combining multipliers")
    class Stacking {

        @Test
        @DisplayName("promotions stack additively: 2x and 3x give 4x, not 6x")
        void promotionsStackAdditively() {
            EarnRule weekend = rule("WEEKEND_2X", EarnRuleType.DAY_OF_WEEK, "2.00",
                    Map.of("days", List.of("SATURDAY", "SUNDAY")));
            EarnRule coffee = rule("COFFEE_3X", EarnRuleType.CATEGORY, "3.00",
                    Map.of("categories", List.of("COFFEE")));

            EarnResult result = engine.evaluate(
                    context("1000", "COFFEE", SATURDAY_EARLY_IST, "1.00",
                            baseRule(), weekend, coffee));

            // base 10 points, multiplier 1 + (2-1) + (3-1) = 4.
            // Multiplying instead would give 6x and 60 points -- and would
            // compound alarmingly as more campaigns are added.
            assertThat(result.effectiveMultiplier()).isEqualByComparingTo("4.00");
            assertThat(result.points()).isEqualTo(40);
            assertThat(codes(result)).containsExactly("BASE_EARN", "WEEKEND_2X", "COFFEE_3X");
        }

        @Test
        @DisplayName("the tier bonus applies on top, and rounding happens only at the end")
        void tierBonusAppliesAndRoundsOnce() {
            // base = floor(350/100) = 3, no promotions, Gold = 1.25
            //   3 x 1.25 = 3.75 -> floored once, at the end, to 3.
            EarnResult result = engine.evaluate(
                    context("350", "COFFEE", MIDWEEK, "1.25", baseRule()));

            assertThat(result.points()).isEqualTo(3);
        }

        @Test
        @DisplayName("everything together: weekend coffee for a Gold member")
        void fullStack() {
            EarnRule weekend = rule("WEEKEND_2X", EarnRuleType.DAY_OF_WEEK, "2.00",
                    Map.of("days", List.of("SATURDAY", "SUNDAY")));
            EarnRule coffee = rule("COFFEE_3X", EarnRuleType.CATEGORY, "3.00",
                    Map.of("categories", List.of("COFFEE")));

            // base 10, promotions 4x, tier 1.25  ->  10 x 4 x 1.25 = 50
            EarnResult result = engine.evaluate(
                    context("1000", "COFFEE", SATURDAY_EARLY_IST, "1.25",
                            baseRule(), weekend, coffee));

            assertThat(result.points()).isEqualTo(50);
        }
    }

    // -----------------------------------------------------------------------
    // fixtures
    // -----------------------------------------------------------------------

    private static EarnRule baseRule() {
        EarnRule rule = rule("BASE_EARN", EarnRuleType.BASE, "1.00",
                Map.of("pointsPerUnit", 100));
        rule.setPriority(0);
        return rule;
    }

    private static EarnRule rule(String code, EarnRuleType type, String multiplier,
                                 Map<String, Object> conditions) {
        EarnRule rule = new EarnRule();
        rule.setCode(code);
        rule.setName(code);
        rule.setRuleType(type);
        rule.setMultiplier(new BigDecimal(multiplier));
        rule.setConditions(new HashMap<>(conditions));
        rule.setActive(true);
        rule.setPriority(10);
        return rule;
    }

    private static EarnContext context(String amount, String category, Instant occurredAt,
                                       String tierMultiplier, EarnRule... rules) {
        return new EarnContext(
                new BigDecimal(amount),
                category,
                occurredAt,
                IST,
                new BigDecimal(tierMultiplier),
                List.of(rules));
    }

    private static List<String> codes(EarnResult result) {
        return result.appliedRules().stream().map(AppliedRule::code).toList();
    }
}
