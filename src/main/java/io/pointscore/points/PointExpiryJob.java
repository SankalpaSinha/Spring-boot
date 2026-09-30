package io.pointscore.points;

import io.pointscore.points.PointExpiryService.ExpiryOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the expiry sweep nightly.
 *
 * <p>Off under the {@code test} profile via {@code pointscore.expiry-job.enabled}
 * (see application-test.yml), so a scheduled sweep cannot fire in the middle
 * of an unrelated test and quietly change balances underneath it.
 *
 * <p>A single-instance scheduler is a deliberate simplification. Running two
 * copies of this application would run the sweep twice; the second pass would
 * find nothing to do, because expired lots are drained by the first -- so the
 * outcome is still correct, just wasteful. A production deployment would use
 * a locking scheduler such as ShedLock.
 */
@Component
@ConditionalOnProperty(name = "pointscore.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class PointExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PointExpiryJob.class);

    private final PointExpiryService pointExpiryService;

    public PointExpiryJob(PointExpiryService pointExpiryService) {
        this.pointExpiryService = pointExpiryService;
    }

    /** 02:00 in the programme's timezone, when traffic is lowest. */
    @Scheduled(cron = "0 0 2 * * *", zone = "${pointscore.zone}")
    public void sweep() {
        ExpiryOutcome outcome = pointExpiryService.expireNow();
        if (outcome.lotsExpired() > 0) {
            log.info("Expiry sweep: {} points across {} lots",
                    outcome.pointsExpired(), outcome.lotsExpired());
        }
    }
}
