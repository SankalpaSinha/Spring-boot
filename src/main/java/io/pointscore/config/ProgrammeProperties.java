package io.pointscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Policy that belongs to the brand running the programme, not to the code.
 *
 * @param zone              the brand's local timezone; decides what counts as a
 *                          weekend and when an expiry date falls
 * @param pointExpiryMonths how long earned points survive
 */
@ConfigurationProperties(prefix = "pointscore")
public record ProgrammeProperties(ZoneId zone, int pointExpiryMonths) {

    public ProgrammeProperties {
        if (pointExpiryMonths <= 0) {
            throw new IllegalArgumentException(
                    "pointscore.point-expiry-months must be positive, was " + pointExpiryMonths);
        }
    }

    /**
     * When points earned at a given moment should expire.
     *
     * <p>Deliberately routed through the programme's timezone rather than added
     * to the Instant directly. "Twelve months" is a calendar idea, not a fixed
     * number of seconds -- months have different lengths, and adding
     * {@code 365 days} would drift by a day across a leap year and land members'
     * points on the wrong date. {@link java.time.ZonedDateTime#plusMonths} knows
     * the calendar rules, including that 31 January plus one month is 28
     * February.
     */
    public Instant expiryFor(Instant earnedAt) {
        return earnedAt.atZone(zone).plusMonths(pointExpiryMonths).toInstant();
    }
}
