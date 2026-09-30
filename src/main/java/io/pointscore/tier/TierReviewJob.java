package io.pointscore.tier;

import io.pointscore.tier.TierReviewService.ReviewOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Runs the tier review nightly, an hour after the expiry sweep.
 *
 * <p>Off under the {@code test} profile (see application-test.yml), so a
 * scheduled review cannot fire in the middle of a test and move a tier
 * underneath it. The same single-instance caveat as {@code PointExpiryJob}
 * applies: two copies of the application would review everyone twice, which
 * is wasteful but not wrong, because a second pass finds nothing to change.
 */
@Component
@ConditionalOnProperty(name = "pointscore.tier-review-job.enabled", havingValue = "true", matchIfMissing = true)
public class TierReviewJob {

    private static final Logger log = LoggerFactory.getLogger(TierReviewJob.class);

    private final TierReviewService tierReviewService;

    public TierReviewJob(TierReviewService tierReviewService) {
        this.tierReviewService = tierReviewService;
    }

    /** 03:00 in the programme's timezone: after expiry has run at 02:00. */
    @Scheduled(cron = "0 0 3 * * *", zone = "${pointscore.zone}")
    public void sweep() {
        ReviewOutcome outcome = tierReviewService.reviewAll(Instant.now());
        log.info("Tier review: {} members, {} upgrades, {} downgrades, {} failed",
                outcome.reviewed(), outcome.upgrades(), outcome.downgrades(), outcome.failed());
    }
}
