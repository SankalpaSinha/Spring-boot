package io.pointscore.points;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kills points that have passed their expiry date.
 *
 * <p>Expiry is the quiet half of a loyalty programme. Points are a liability on
 * the brand's balance sheet, and letting them live for ever means the liability
 * only grows. It is also the part members notice most sharply, which is why
 * every expiry is recorded rather than simply subtracted.
 */
@Service
public class PointExpiryService {

    private static final Logger log = LoggerFactory.getLogger(PointExpiryService.class);

    private final PointLotRepository pointLotRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public PointExpiryService(PointLotRepository pointLotRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.pointLotRepository = pointLotRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public record ExpiryOutcome(int lotsExpired, int pointsExpired) {

        static ExpiryOutcome nothing() {
            return new ExpiryOutcome(0, 0);
        }
    }

    /**
     * Expires every lot whose date has passed.
     *
     * @param asOf the moment to judge against -- a parameter rather than
     *             {@code Instant.now()} so tests can run the job at any point
     *             in time without waiting a year. Code that calls now() deep
     *             inside itself is code you cannot test.
     */
    @Transactional
    public ExpiryOutcome expireLotsAsOf(Instant asOf) {
        List<PointLot> expired = pointLotRepository.findExpiredLots(asOf);
        if (expired.isEmpty()) {
            return ExpiryOutcome.nothing();
        }

        int pointsExpired = 0;
        for (PointLot lot : expired) {
            int remaining = lot.getPointsRemaining();
            lot.draw(remaining);
            ledgerEntryRepository.save(LedgerEntry.of(
                    lot.getMember(), LedgerEntryType.EXPIRE, -remaining,
                    lot, "EXPIRY", null, "Points expired"));
            pointsExpired += remaining;
        }
        pointLotRepository.saveAll(expired);

        log.info("Expired {} points across {} lots as of {}", pointsExpired, expired.size(), asOf);
        return new ExpiryOutcome(expired.size(), pointsExpired);
    }

    /**
     * Convenience for the scheduled job. Transactional in its own right: a
     * call from here to {@link #expireLotsAsOf} is a self-invocation that
     * never passes through the Spring proxy, so without this annotation the
     * production sweep would run with no transaction at all -- each lot
     * update and its ledger row committing separately.
     */
    @Transactional
    public ExpiryOutcome expireNow() {
        return expireLotsAsOf(Instant.now());
    }
}
