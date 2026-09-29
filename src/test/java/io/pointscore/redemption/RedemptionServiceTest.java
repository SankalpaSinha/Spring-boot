package io.pointscore.redemption;

import io.pointscore.AbstractIntegrationTest;
import io.pointscore.common.InsufficientPointsException;
import io.pointscore.common.OutOfStockException;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import io.pointscore.points.LedgerEntry;
import io.pointscore.points.LedgerEntryRepository;
import io.pointscore.points.LedgerEntryType;
import io.pointscore.points.PointLot;
import io.pointscore.points.PointLotRepository;
import io.pointscore.redemption.RedemptionService.RedemptionOutcome;
import io.pointscore.reward.Reward;
import io.pointscore.reward.RewardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milestone 3, specified as tests.
 *
 * <p>These run against a real Postgres because what they are testing --
 * {@code SELECT ... FOR UPDATE} -- is database behaviour. An in-memory database
 * would let every one of these pass while the production code stayed broken.
 */
class RedemptionServiceTest extends AbstractIntegrationTest {

    @Autowired private RedemptionService redemptionService;
    @Autowired private RedemptionRepository redemptionRepository;
    @Autowired private PointLotRepository pointLotRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private RewardRepository rewardRepository;
    @Autowired private MemberService memberService;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("spends the points and records one ledger row per lot drawn")
    void spendsPointsAndRecordsTheLedger() {
        Member member = givenMemberWithLots(500);
        Reward reward = givenReward("LATTE", 250, 10);

        RedemptionOutcome outcome =
                redemptionService.redeem(member.getId(), reward.getId(), key());

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.redemption().getPointsSpent()).isEqualTo(250);
        assertThat(outcome.redemption().getStatus()).isEqualTo(RedemptionStatus.COMPLETED);

        assertThat(balanceOf(member)).isEqualTo(250);
        assertThat(livePointsOf(member)).isEqualTo(250);

        List<LedgerEntry> redeemRows = ledgerRowsOf(member, LedgerEntryType.REDEEM);
        assertThat(redeemRows).hasSize(1);
        // Negative, always. The database CHECK rejects a positive REDEEM.
        assertThat(redeemRows.getFirst().getPoints()).isEqualTo(-250);
        assertThat(redeemRows.getFirst().getLot()).isNotNull();
    }

    @Test
    @DisplayName("drains the soonest-expiring lot first, and spills into the next")
    void drawsFromSoonestExpiringLotFirst() {
        // 100 points expiring in 30 days, 400 expiring in 300 days.
        Member member = givenMemberWithLots(100, 400);
        Reward reward = givenReward("BEANS", 250, 10);

        redemptionService.redeem(member.getId(), reward.getId(), key());

        List<PointLot> lots = lotsOf(member);
        // The near-expiry lot is emptied first -- those points were about to be
        // lost anyway, so spending them is the member-friendly order.
        assertThat(lots.get(0).getPointsRemaining()).isZero();
        assertThat(lots.get(1).getPointsRemaining()).isEqualTo(250);

        // One ledger row per lot touched, so it is possible to say exactly
        // which points were spent.
        assertThat(ledgerRowsOf(member, LedgerEntryType.REDEEM))
                .hasSize(2)
                .extracting(LedgerEntry::getPoints)
                .containsExactlyInAnyOrder(-100, -150);
    }

    @Test
    @DisplayName("refuses when the balance is short, and changes nothing")
    void refusesWhenBalanceIsTooLow() {
        Member member = givenMemberWithLots(100);
        Reward reward = givenReward("MUG", 800, 10);

        assertThatThrownBy(() -> redemptionService.redeem(member.getId(), reward.getId(), key()))
                .isInstanceOf(InsufficientPointsException.class);

        // A failed redemption must leave no trace: same balance, no stock taken.
        assertThat(balanceOf(member)).isEqualTo(100);
        assertThat(livePointsOf(member)).isEqualTo(100);
        assertThat(rewardRepository.findById(reward.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(ledgerRowsOf(member, LedgerEntryType.REDEEM)).isEmpty();
    }

    @Test
    @DisplayName("a retry with the same idempotency key spends only once")
    void retryWithSameKeySpendsOnce() {
        Member member = givenMemberWithLots(500);
        Reward reward = givenReward("LATTE", 250, 10);
        String idempotencyKey = key();

        RedemptionOutcome first =
                redemptionService.redeem(member.getId(), reward.getId(), idempotencyKey);
        RedemptionOutcome retry =
                redemptionService.redeem(member.getId(), reward.getId(), idempotencyKey);

        assertThat(first.duplicate()).isFalse();
        assertThat(retry.duplicate()).isTrue();
        // Same redemption, not a second one.
        assertThat(retry.redemption().getId()).isEqualTo(first.redemption().getId());

        assertThat(balanceOf(member)).isEqualTo(250);
        assertThat(redemptionRepository.count()).isPositive();
        assertThat(ledgerRowsOf(member, LedgerEntryType.REDEEM)).hasSize(1);
        // Stock taken once, not twice.
        assertThat(rewardRepository.findById(reward.getId()).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    @DisplayName("refuses when the reward is out of stock")
    void refusesWhenOutOfStock() {
        Member member = givenMemberWithLots(5000);
        Reward reward = givenReward("RARE", 100, 0);

        assertThatThrownBy(() -> redemptionService.redeem(member.getId(), reward.getId(), key()))
                .isInstanceOf(OutOfStockException.class);

        assertThat(balanceOf(member)).isEqualTo(5000);
    }

    /**
     * The one that matters.
     *
     * <p>Twenty threads try to redeem a 100-point reward for a member holding
     * 300 points, all at the same instant. Exactly three can succeed.
     *
     * <p>Without {@code SELECT ... FOR UPDATE} this fails: the threads all read
     * a balance of 300 before any of them has committed a deduction, all decide
     * they can afford it, and the member finishes with a negative balance and
     * twenty rewards. With the lock, the second thread blocks until the first
     * commits, then re-reads the truth.
     */
    @Test
    @DisplayName("twenty simultaneous redemptions never overdraw the balance")
    void concurrentRedemptionsNeverOverdraw() throws Exception {
        int attempts = 20;
        int cost = 100;
        Member member = givenMemberWithLots(300);
        Reward reward = givenReward("POPULAR", cost, 1000);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        // Every thread waits on the same latch, so they are released together
        // rather than trickling in one at a time -- which would serialise
        // naturally and prove nothing.
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                pool.submit(() -> {
                    try {
                        startLine.await();
                        // A distinct key each time: these are twenty different
                        // redemption attempts, not one retried twenty times.
                        redemptionService.redeem(member.getId(), reward.getId(), key());
                        succeeded.incrementAndGet();
                    } catch (InsufficientPointsException expected) {
                        insufficient.incrementAndGet();
                    } catch (Throwable other) {
                        synchronized (unexpected) {
                            unexpected.add(other);
                        }
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startLine.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS))
                    .as("all attempts finished without deadlocking")
                    .isTrue();
        }

        assertThat(unexpected).as("no unexpected failures").isEmpty();

        // The headline assertion.
        assertThat(balanceOf(member)).as("balance must never go negative").isNotNegative();

        assertThat(succeeded.get()).as("300 points at 100 each funds exactly three").isEqualTo(3);
        assertThat(insufficient.get()).isEqualTo(attempts - 3);
        assertThat(balanceOf(member)).isZero();

        // Stock and points must tell the same story: three rewards left the
        // shelf because three were paid for.
        assertThat(rewardRepository.findById(reward.getId()).orElseThrow().getStock())
                .isEqualTo(1000 - 3);

        assertInvariantHolds(member);
    }

    @Test
    @DisplayName("the ledger and the lots never disagree")
    void ledgerAndLotsStayInAgreement() {
        Member member = givenMemberWithLots(200, 300);
        Reward reward = givenReward("SNACK", 150, 10);

        redemptionService.redeem(member.getId(), reward.getId(), key());
        redemptionService.redeem(member.getId(), reward.getId(), key());

        assertInvariantHolds(member);
        assertThat(balanceOf(member)).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * The invariant the whole design rests on: the append-only history and the
     * spendable lots must always add up to the same number. If these ever
     * diverge, some code changed a lot without writing its ledger row.
     */
    private void assertInvariantHolds(Member member) {
        assertThat(ledgerEntryRepository.balanceOf(member.getId()))
                .as("ledger sum must equal live lot total")
                .isEqualTo(pointLotRepository.sumLivePoints(member.getId()));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private int balanceOf(Member member) {
        return ledgerEntryRepository.balanceOf(member.getId());
    }

    private int livePointsOf(Member member) {
        return pointLotRepository.sumLivePoints(member.getId());
    }

    /** Every lot the member holds, drained or not, in expiry order. */
    private List<PointLot> lotsOf(Member member) {
        return tx.execute(status -> pointLotRepository.findAll().stream()
                .filter(lot -> lot.getMember().getId().equals(member.getId()))
                .sorted(Comparator.comparing(PointLot::getExpiresAt))
                .toList());
    }

    private List<LedgerEntry> ledgerRowsOf(Member member, LedgerEntryType type) {
        return tx.execute(status -> ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getMember().getId().equals(member.getId()))
                .filter(entry -> entry.getEntryType() == type)
                .toList());
    }

    /**
     * A member holding the given lots, each expiring later than the last.
     * Committed before the test proceeds, so concurrent threads can see it.
     */
    private Member givenMemberWithLots(int... lotSizes) {
        return tx.execute(status -> {
            Member member = memberService.enrol(
                    "Test Member", "member-" + UUID.randomUUID() + "@example.com");

            Instant now = Instant.now();
            int daysOut = 30;
            for (int points : lotSizes) {
                PointLot lot = pointLotRepository.save(PointLot.of(
                        member, null, points,
                        now.minus(1, ChronoUnit.DAYS),
                        now.plus(daysOut, ChronoUnit.DAYS)));

                // Seeded through the ledger too, so the invariant starts true.
                ledgerEntryRepository.save(LedgerEntry.of(
                        member, LedgerEntryType.EARN, points, lot,
                        "TEST", null, "seeded lot"));
                daysOut += 270;
            }
            return member;
        });
    }

    private Reward givenReward(String code, int costPoints, int stock) {
        return tx.execute(status -> {
            Reward reward = new Reward();
            reward.setCode(code + "-" + UUID.randomUUID());
            reward.setName(code);
            reward.setCostPoints(costPoints);
            reward.setStock(stock);
            reward.setActive(true);
            return rewardRepository.save(reward);
        });
    }
}
