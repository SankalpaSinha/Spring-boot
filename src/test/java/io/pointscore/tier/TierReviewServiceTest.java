package io.pointscore.tier;

import io.pointscore.AbstractIntegrationTest;
import io.pointscore.member.Member;
import io.pointscore.member.MemberRepository;
import io.pointscore.member.MemberService;
import io.pointscore.tier.TierReviewService.ReviewOutcome;
import io.pointscore.transaction.Transaction;
import io.pointscore.transaction.TransactionRepository;
import io.pointscore.transaction.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The two triggers for tier movement: a purchase, and the nightly review. */
class TierReviewServiceTest extends AbstractIntegrationTest {

    @Autowired private TierReviewService tierReviewService;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TierChangeRepository tierChangeRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberService memberService;
    @Autowired private TransactionTemplate tx;

    @Test
    @DisplayName("a qualifying purchase upgrades the member on the spot")
    void purchaseUpgradesImmediately() {
        Member member = givenMember();
        assertThat(tierOf(member)).isEqualTo("SILVER");

        // A weekday, non-coffee purchase: 550,000 / 100 = 5,500 base points,
        // over Gold's 5,000. Any promotion that happens to stack only adds.
        tx.executeWithoutResult(status -> transactionService.ingest(
                member.getId(), "TIER-" + UUID.randomUUID(), new BigDecimal("550000.00"),
                "GENERAL", Instant.parse("2026-09-30T06:00:00Z")));

        assertThat(tierOf(member)).isNotEqualTo("SILVER");
        List<TierChange> history = historyOf(member);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getReason()).isEqualTo(TierChange.Reason.UPGRADE);
    }

    @Test
    @DisplayName("the nightly review upgrades, and later downgrades, a member who never buys again")
    void reviewMovesTiersBothWays() {
        Member member = givenMember();
        givenEarnedPoints(member, 6_000, daysAgo(30));
        assertThat(tierOf(member)).isEqualTo("SILVER");

        ReviewOutcome tonight = tierReviewService.reviewAll(Instant.now());
        assertThat(tonight.reviewed()).isGreaterThanOrEqualTo(1);
        assertThat(tonight.upgrades()).isGreaterThanOrEqualTo(1);
        assertThat(tonight.failed()).isZero();
        assertThat(tierOf(member)).isEqualTo("GOLD");

        // Eighteen months on, those earnings have left the window.
        ReviewOutcome later = tierReviewService.reviewAll(Instant.now().plus(550, ChronoUnit.DAYS));
        assertThat(later.downgrades()).isGreaterThanOrEqualTo(1);
        assertThat(tierOf(member)).isEqualTo("SILVER");

        assertThat(historyOf(member))
                .extracting(TierChange::getReason)
                .containsExactly(TierChange.Reason.DOWNGRADE, TierChange.Reason.UPGRADE);
    }

    @Test
    @DisplayName("a member with no purchases is reviewed and left alone")
    void reviewLeavesUnqualifiedMembersAlone() {
        Member member = givenMember();

        tierReviewService.reviewAll(Instant.now());

        assertThat(tierOf(member)).isEqualTo("SILVER");
        assertThat(historyOf(member)).isEmpty();
    }

    // ----------------------------------------------------------------- helpers

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private String tierOf(Member member) {
        return tx.execute(status -> memberRepository.findById(member.getId())
                .orElseThrow().getTier().getCode());
    }

    private List<TierChange> historyOf(Member member) {
        return tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(member.getId());
    }

    private Member givenMember() {
        return tx.execute(status -> memberService.enrol(
                "Review Test", "review-" + UUID.randomUUID() + "@example.com"));
    }

    private void givenEarnedPoints(Member member, int points, Instant occurredAt) {
        tx.executeWithoutResult(status -> {
            Transaction transaction = new Transaction();
            transaction.setMember(member);
            transaction.setExternalRef("REVIEW-" + UUID.randomUUID());
            transaction.setAmount(new BigDecimal(points * 100));
            transaction.setCategory("GENERAL");
            transaction.setOccurredAt(occurredAt);
            transaction.setPointsAwarded(points);
            transactionRepository.save(transaction);
        });
    }
}
