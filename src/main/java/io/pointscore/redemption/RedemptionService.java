package io.pointscore.redemption;

import io.pointscore.common.InsufficientPointsException;
import io.pointscore.common.OutOfStockException;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import io.pointscore.points.LedgerEntry;
import io.pointscore.points.LedgerEntryRepository;
import io.pointscore.points.LedgerEntryType;
import io.pointscore.points.PointLot;
import io.pointscore.points.PointLotRepository;
import io.pointscore.reward.Reward;
import io.pointscore.reward.RewardRepository;
import io.pointscore.reward.RewardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spends points on rewards.
 *
 * <p>This is the most dangerous code in the project, because two things can go
 * wrong here that cannot go wrong anywhere else:
 *
 * <ol>
 *   <li><b>A lost update.</b> Two redemptions arriving at the same instant both
 *       read a balance of 300, both decide a 200-point reward is affordable, and
 *       the member ends up at −100 having received two rewards. Nothing in the
 *       code looks wrong; the two requests simply interleaved.</li>
 *
 *   <li><b>A double spend on retry.</b> A phone loses signal after the server
 *       committed but before the response arrived. The app retries. Without an
 *       idempotency key the member pays twice for one reward.</li>
 * </ol>
 *
 * <p>These are different problems with different fixes, and doing one does not
 * protect you from the other. The row lock stops concurrent requests from
 * trampling each other; the idempotency key stops the <em>same</em> request
 * being applied twice.
 */
@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);

    private final RedemptionRepository redemptionRepository;
    private final PointLotRepository pointLotRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RewardRepository rewardRepository;
    private final RewardService rewardService;
    private final MemberService memberService;

    public RedemptionService(RedemptionRepository redemptionRepository,
                             PointLotRepository pointLotRepository,
                             LedgerEntryRepository ledgerEntryRepository,
                             RewardRepository rewardRepository,
                             RewardService rewardService,
                             MemberService memberService) {
        this.redemptionRepository = redemptionRepository;
        this.pointLotRepository = pointLotRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.rewardRepository = rewardRepository;
        this.rewardService = rewardService;
        this.memberService = memberService;
    }

    /** The redemption, and whether this call was a replay of one already done. */
    public record RedemptionOutcome(Redemption redemption, boolean duplicate) {
    }

    /**
     * Redeems a reward for a member.
     *
     * @param idempotencyKey caller-supplied; the same key must never spend twice
     */
    @Transactional
    public RedemptionOutcome redeem(Long memberId, Long rewardId, String idempotencyKey) {
        // ------------------------------------------------------------------
        // YOUR TASK (milestone 3)
        //
        // Work through it in this order. The order is not arbitrary -- doing
        // step 4 before step 3 reintroduces the very race the lock prevents.
        //
        //   1. IDEMPOTENCY FAST PATH
        //      redemptionRepository.findByIdempotencyKey(idempotencyKey)
        //      If one exists, this is a retry: return it with duplicate = true.
        //      Do not spend anything.
        //
        //   2. LOAD
        //      memberService.require(memberId)      -> Member
        //      rewardService.require(rewardId)      -> Reward
        //      If the reward is not active, throw
        //      new NotFoundException("reward", rewardId) -- an inactive reward
        //      should not be redeemable even if someone knows its id.
        //
        //   3. LOCK THE POINTS, THEN COUNT THEM
        //      pointLotRepository.lockLiveLotsForMember(memberId)
        //
        //      This is the heart of the milestone. That repository method is
        //      annotated @Lock(PESSIMISTIC_WRITE), so Hibernate emits
        //      SELECT ... FOR UPDATE and Postgres holds those rows until this
        //      transaction commits. A second redemption for the same member
        //      BLOCKS on this line instead of reading a stale balance.
        //
        //      Sum pointsRemaining across the returned lots. Note you must sum
        //      the LOCKED rows -- calling a separate sum query instead would
        //      read outside the lock and defeat the whole exercise.
        //
        //   4. AFFORDABILITY
        //      If the sum is less than reward.getCostPoints(), throw
        //      new InsufficientPointsException(cost, available).
        //
        //   5. STOCK
        //      rewardRepository.decrementStock(rewardId) returns the number of
        //      rows it changed. Zero means somebody else took the last one:
        //      throw new OutOfStockException(reward.getName()).
        //      It is a single conditional UPDATE, so it is safe without a lock.
        //
        //   6. DRAW THE POINTS, SOONEST-EXPIRING FIRST
        //      The locked lots already arrive in that order. Walk them,
        //      calling lot.draw(remainingToPay) on each -- draw() returns how
        //      many it actually gave, which may be fewer than you asked for
        //      when a lot runs dry. Keep going until the cost is covered.
        //
        //      For EVERY lot you draw from, save a LedgerEntry:
        //        LedgerEntry.of(member, LedgerEntryType.REDEEM, -taken, lot,
        //                       "REDEMPTION", redemption.getId(), description)
        //      Note the MINUS. Redemptions are negative; the database has a
        //      CHECK constraint that will reject a positive REDEEM row.
        //
        //      One ledger row per lot, not one per redemption -- that is what
        //      makes it possible to answer which points were spent.
        //
        //   7. SAVE THE REDEMPTION
        //      status COMPLETED, pointsSpent = cost, completedAt = now.
        //      Save it BEFORE the ledger entries if you want its id for refId
        //      (use saveAndFlush to get the id assigned).
        //
        //      Wrap the save in try/catch for DataIntegrityViolationException:
        //      that means a concurrent request with the SAME idempotency key
        //      won the race. Re-read by key and return it with duplicate=true,
        //      exactly as TransactionService does for external_ref.
        //
        // The invariant to preserve: after this method,
        //   SUM(ledger_entries.points) == SUM(point_lots.points_remaining)
        // for this member. A test checks it directly.
        // ------------------------------------------------------------------
        throw new UnsupportedOperationException(
                "milestone 3: implement RedemptionService.redeem -- see the notes above");
    }

    @Transactional(readOnly = true)
    public Page<Redemption> history(Long memberId, Pageable pageable) {
        memberService.require(memberId);
        return redemptionRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public int balanceOf(Long memberId) {
        return ledgerEntryRepository.balanceOf(memberId);
    }
}
