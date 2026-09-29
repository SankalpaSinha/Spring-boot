package io.pointscore.earning;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Everything the engine needs to price one purchase, and nothing else.
 *
 * <p>The rules arrive as an argument rather than being fetched inside the
 * engine. That makes evaluation a pure function of its inputs: the whole of
 * milestone 2 can be unit-tested with plain objects and no database, no Spring
 * context and no mocks. Reaching for a repository inside the engine would make
 * every test need a running Postgres to check arithmetic.
 *
 * @param amount          basket value, in the programme's currency
 * @param category        purchase category, e.g. {@code COFFEE}
 * @param occurredAt      when the purchase happened, as an absolute instant
 * @param programmeZone   the brand's local timezone -- see below
 * @param tierMultiplier  the member's tier bonus, e.g. 1.25 for Gold
 * @param rules           candidate rules; the engine decides which ones apply
 */
public record EarnContext(
        BigDecimal amount,
        String category,
        Instant occurredAt,
        ZoneId programmeZone,
        BigDecimal tierMultiplier,
        List<EarnRule> rules
) {

    /**
     * The day the purchase happened, <em>in the brand's timezone</em>.
     *
     * <p>This is not fussiness. An Instant has no day-of-week until you pick a
     * zone. A coffee bought at 04:00 on Saturday in Hyderabad is still Friday in
     * UTC, so a weekend promotion evaluated in UTC would silently refuse to pay
     * out for the first few hours of every Saturday -- a bug that only shows up
     * in production, only in the early morning, and only for real customers.
     */
    public java.time.DayOfWeek dayOfWeek() {
        return occurredAt.atZone(programmeZone).getDayOfWeek();
    }
}
