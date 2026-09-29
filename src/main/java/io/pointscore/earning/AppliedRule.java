package io.pointscore.earning;

import java.math.BigDecimal;

/**
 * A record of one rule having fired, kept so the API can answer "why did I get
 * 50 points?" instead of just asserting the number. A loyalty programme nobody
 * can explain is a support burden.
 */
public record AppliedRule(String code, String name, BigDecimal multiplier) {

    public static AppliedRule from(EarnRule rule) {
        return new AppliedRule(rule.getCode(), rule.getName(), rule.getMultiplier());
    }
}
