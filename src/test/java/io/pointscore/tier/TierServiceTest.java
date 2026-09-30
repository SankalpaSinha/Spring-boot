package io.pointscore.tier;

import io.pointscore.AbstractIntegrationTest;
import io.pointscore.member.Member;
import io.pointscore.member.MemberRepository;
import io.pointscore.member.MemberService;
import io.pointscore.points.LedgerEntry;
import io.pointscore.points.LedgerEntryRepository;
import io.pointscore.points.LedgerEntryType;
import io.pointscore.tier.TierService.TierAssessment;
import io.pointscore.transaction.Transaction;
import io.pointscore.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 4, part 2: tier qualification.
 *
 * <p>Seeded tiers: SILVER at 0, GOLD at 5,000, PLATINUM at 20,000 points earned
 * in a rolling twelve months.
 */
class TierServiceTest extends AbstractIntegrationTest {

    @Autowired private TierService tierService;
    @Autowired private TierChangeRepository tierChangeRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberService memberService;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("a new member with no purchases stays Silver")
    void newMemberStaysSilver() {
        Member member = givenMember();

        TierAssessment assessment = tierService.recalculate(member.getId(), Instant.now());

        assertThat(assessment.changed()).isFalse();
        assertThat(assessment.currentTier().getCode()).isEqualTo("SILVER");
        assertThat(assessment.qualifyingPoints()).isZero();
        assertThat(tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(member.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("crossing 5,000 earned points promotes to Gold and records the change")
    void promotesToGold() {
        Member member = givenMember();
        givenEarnedPoints(member, 6_000, daysAgo(30));

        TierAssessment assessment = tierService.recalculate(member.getId(), Instant.now());

        assertThat(assessment.changed()).isTrue();
        assertThat(assessment.previousTier().getCode()).isEqualTo("SILVER");
        assertThat(assessment.currentTier().getCode()).isEqualTo("GOLD");
        assertThat(assessment.qualifyingPoints()).isEqualTo(6_000);

        // The member row itself must move, not just the returned assessment.
        assertThat(tierOf(member)).isEqualTo("GOLD");

        assertThat(tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(member.getId()))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.getReason()).isEqualTo(TierChange.Reason.UPGRADE);
                    assertThat(change.getQualifyingPoints()).isEqualTo(6_000);
                });
    }

    @Test
    @DisplayName("20,000 earned points reaches Platinum, skipping past Gold")
    void promotesStraightToPlatinum() {
        Member member = givenMember();
        givenEarnedPoints(member, 25_000, daysAgo(60));

        TierAssessment assessment = tierService.recalculate(member.getId(), Instant.now());

        // findHighestQualifying returns the best tier earned, not the next one
        // up -- a big spender should not have to climb one rung at a time.
        assertThat(assessment.currentTier().getCode()).isEqualTo("PLATINUM");
    }

    @Test
    @DisplayName("the threshold is inclusive: exactly 5,000 qualifies for Gold")
    void thresholdIsInclusive() {
        Member member = givenMember();
        givenEarnedPoints(member, 5_000, daysAgo(10));

        assertThat(tierService.recalculate(member.getId(), Instant.now())
                .currentTier().getCode()).isEqualTo("GOLD");
    }

    @Test
    @DisplayName("purchases older than the window stop counting, so the member is demoted")
    void demotesWhenEarningsAgeOut() {
        Member member = givenMember();
        givenEarnedPoints(member, 6_000, daysAgo(30));

        tierService.recalculate(member.getId(), Instant.now());
        assertThat(tierOf(member)).isEqualTo("GOLD");

        // Eighteen months on, that purchase has fallen out of the rolling
        // twelve-month window and no longer supports the status.
        TierAssessment later = tierService.recalculate(
                member.getId(), Instant.now().plus(550, ChronoUnit.DAYS));

        assertThat(later.changed()).isTrue();
        assertThat(later.currentTier().getCode()).isEqualTo("SILVER");
        assertThat(later.qualifyingPoints()).isZero();
        assertThat(tierOf(member)).isEqualTo("SILVER");

        assertThat(tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(member.getId()))
                .hasSize(2)
                .first()
                .satisfies(change ->
                        assertThat(change.getReason()).isEqualTo(TierChange.Reason.DOWNGRADE));
    }

    /**
     * The test that matters most.
     *
     * <p>If this fails, the implementation is reading the member's balance
     * instead of their earnings -- and the programme would punish people for
     * using it, which is the opposite of what a loyalty scheme is for.
     */
    @Test
    @DisplayName("spending every last point does not cost the member their tier")
    void spendingPointsDoesNotDemote() {
        Member member = givenMember();
        givenEarnedPoints(member, 8_000, daysAgo(20));

        tierService.recalculate(member.getId(), Instant.now());
        assertThat(tierOf(member)).isEqualTo("GOLD");

        // The member now redeems everything. Balance is zero; earnings are not.
        spendEverything(member, 8_000);

        TierAssessment afterSpending = tierService.recalculate(member.getId(), Instant.now());

        assertThat(afterSpending.changed()).isFalse();
        assertThat(afterSpending.currentTier().getCode()).isEqualTo("GOLD");
        assertThat(afterSpending.qualifyingPoints()).isEqualTo(8_000);
        assertThat(tierOf(member)).isEqualTo("GOLD");
    }

    @Test
    @DisplayName("recalculating twice in a row reports no change the second time")
    void isStableOnRepeat() {
        Member member = givenMember();
        givenEarnedPoints(member, 9_000, daysAgo(5));

        assertThat(tierService.recalculate(member.getId(), Instant.now()).changed()).isTrue();
        assertThat(tierService.recalculate(member.getId(), Instant.now()).changed()).isFalse();

        // One movement recorded, not two. The audit trail must not fill up with
        // "Gold -> Gold" rows every time the job runs.
        assertThat(tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(member.getId()))
                .hasSize(1);
    }

    // ----------------------------------------------------------------- helpers

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private String tierOf(Member member) {
        return tx.execute(status -> memberRepository.findById(member.getId())
                .orElseThrow().getTier().getCode());
    }

    private Member givenMember() {
        return tx.execute(status -> memberService.enrol(
                "Tier Test", "tier-" + UUID.randomUUID() + "@example.com"));
    }

    /** A purchase that awarded the given points at the given time. */
    private void givenEarnedPoints(Member member, int points, Instant occurredAt) {
        tx.executeWithoutResult(status -> {
            Transaction transaction = new Transaction();
            transaction.setMember(member);
            transaction.setExternalRef("TIER-" + UUID.randomUUID());
            transaction.setAmount(new BigDecimal(points * 100));
            transaction.setCategory("GENERAL");
            transaction.setOccurredAt(occurredAt);
            transaction.setPointsAwarded(points);
            transactionRepository.save(transaction);
        });
    }

    /**
     * Drains the balance without touching the transaction history.
     *
     * <p>Recorded as a ledger adjustment rather than a real redemption: this
     * test is about tier qualification, and the redemption path has its own.
     */
    private void spendEverything(Member member, int points) {
        tx.executeWithoutResult(status -> ledgerEntryRepository.save(
                LedgerEntry.of(member, LedgerEntryType.ADJUST, -points,
                        null, "TEST", null, "spent in test")));
    }
}
