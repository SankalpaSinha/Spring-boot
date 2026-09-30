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
        // ------------------------------------------------------------------
        // YOUR TASK (milestone 4, part 1)
        //
        //   1. pointLotRepository.findExpiredLots(asOf)
        //      Returns lots that still have points AND whose expires_at has
        //      passed. Both conditions matter: a lot already drained to zero
        //      by redemption must not produce a ledger row of -0, which the
        //      database rejects anyway (points <> 0).
        //
        //   2. If the list is empty, return ExpiryOutcome.nothing().
        //
        //   3. For each lot:
        //        - remember how many points are left  (getPointsRemaining())
        //        - take them all: lot.draw(thatMany)
        //        - write the ledger row:
        //            LedgerEntry.of(lot.getMember(), LedgerEntryType.EXPIRE,
        //                           -points, lot, "EXPIRY", null,
        //                           "Points expired")
        //          Negative again -- EXPIRE reduces the balance, and the CHECK
        //          constraint enforces it.
        //
        //   4. Return the count of lots and the total points killed.
        //
        // Two things the tests check that are easy to miss:
        //
        //   - Running the job twice must not expire anything the second time.
        //     Step 1 handles this for free IF you actually drain the lot;
        //     a lot left with points still in it would be found again.
        //
        //   - A lot that was partly spent expires only what remains. Someone
        //     who earned 100 and spent 60 loses 40, not 100.
        // ------------------------------------------------------------------
        throw new UnsupportedOperationException(
                "milestone 4: implement PointExpiryService.expireLotsAsOf");
    }

    /** Convenience for the scheduled job. */
    public ExpiryOutcome expireNow() {
        return expireLotsAsOf(Instant.now());
    }
}
