package io.pointscore.points;

import io.pointscore.AbstractIntegrationTest;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import io.pointscore.points.PointExpiryService.ExpiryOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Milestone 4, part 1: points expiry. */
class PointExpiryServiceTest extends AbstractIntegrationTest {

    @Autowired private PointExpiryService pointExpiryService;
    @Autowired private PointLotRepository pointLotRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private MemberService memberService;
    @Autowired private TransactionTemplate tx;

    /**
     * The sweep is global -- it is a nightly job, not a per-member call -- so
     * its counts include every lot in the database. Other test classes leave
     * lots behind, and the reused container keeps them between runs. Draining
     * everything first, far in the future, means each test's outcome counts
     * only the lots it seeded. Going through the service rather than TRUNCATE
     * keeps the ledger/lot invariant intact for the members involved.
     */
    @BeforeEach
    void clearTheBoard() {
        pointExpiryService.expireLotsAsOf(Instant.now().plus(10_000, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("expires a lot whose date has passed, and writes a negative ledger row")
    void expiresLotsPastTheirDate() {
        Member member = givenMember();
        givenLot(member, 300, daysAgo(400), daysAgo(35));

        ExpiryOutcome outcome = pointExpiryService.expireLotsAsOf(Instant.now());

        assertThat(outcome.lotsExpired()).isEqualTo(1);
        assertThat(outcome.pointsExpired()).isEqualTo(300);

        assertThat(balanceOf(member)).isZero();
        assertThat(livePointsOf(member)).isZero();

        List<LedgerEntry> expiries = ledgerRowsOf(member, LedgerEntryType.EXPIRE);
        assertThat(expiries).hasSize(1);
        assertThat(expiries.getFirst().getPoints()).isEqualTo(-300);
    }

    @Test
    @DisplayName("the job's entry point expires through the proxy, atomically")
    void expireNowIsTransactional() {
        Member member = givenMember();
        givenLot(member, 120, daysAgo(400), daysAgo(3));

        ExpiryOutcome outcome = pointExpiryService.expireNow();

        assertThat(outcome.pointsExpired()).isEqualTo(120);
        assertThat(balanceOf(member)).isZero();
        assertThat(livePointsOf(member)).isZero();
        assertThat(balanceOf(member)).isEqualTo(livePointsOf(member));
    }

    @Test
    @DisplayName("leaves lots that have not yet expired completely alone")
    void leavesLiveLotsAlone() {
        Member member = givenMember();
        givenLot(member, 500, daysAgo(10), daysFromNow(300));

        ExpiryOutcome outcome = pointExpiryService.expireLotsAsOf(Instant.now());

        assertThat(outcome.lotsExpired()).isZero();
        assertThat(balanceOf(member)).isEqualTo(500);
        assertThat(ledgerRowsOf(member, LedgerEntryType.EXPIRE)).isEmpty();
    }

    @Test
    @DisplayName("expires only what is left of a partly spent lot")
    void expiresOnlyTheRemainder() {
        Member member = givenMember();
        PointLot lot = givenLot(member, 100, daysAgo(400), daysAgo(5));

        // Simulate 60 already spent: draw them and record it, keeping the
        // ledger and the lot in agreement as every other path does.
        tx.executeWithoutResult(status -> {
            PointLot managed = pointLotRepository.findById(lot.getId()).orElseThrow();
            managed.draw(60);
            ledgerEntryRepository.save(LedgerEntry.of(
                    member, LedgerEntryType.REDEEM, -60, managed, "TEST", null, "spent earlier"));
            pointLotRepository.save(managed);
        });

        ExpiryOutcome outcome = pointExpiryService.expireLotsAsOf(Instant.now());

        // 40 remained, so 40 expire -- not the original 100.
        assertThat(outcome.pointsExpired()).isEqualTo(40);
        assertThat(ledgerRowsOf(member, LedgerEntryType.EXPIRE))
                .singleElement()
                .extracting(LedgerEntry::getPoints)
                .isEqualTo(-40);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    @DisplayName("running the sweep twice expires nothing the second time")
    void isIdempotent() {
        Member member = givenMember();
        givenLot(member, 250, daysAgo(400), daysAgo(1));

        ExpiryOutcome first = pointExpiryService.expireLotsAsOf(Instant.now());
        ExpiryOutcome second = pointExpiryService.expireLotsAsOf(Instant.now());

        assertThat(first.pointsExpired()).isEqualTo(250);
        // A job that ran twice on a bad night must not double-charge the member.
        assertThat(second.lotsExpired()).isZero();
        assertThat(second.pointsExpired()).isZero();
        assertThat(ledgerRowsOf(member, LedgerEntryType.EXPIRE)).hasSize(1);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    @DisplayName("expires the old lot and leaves the newer one untouched")
    void expiresOnlyTheOldLot() {
        Member member = givenMember();
        givenLot(member, 100, daysAgo(400), daysAgo(2));
        givenLot(member, 700, daysAgo(10), daysFromNow(300));

        pointExpiryService.expireLotsAsOf(Instant.now());

        assertThat(balanceOf(member)).isEqualTo(700);
        assertThat(livePointsOf(member)).isEqualTo(700);

        List<PointLot> lots = lotsOf(member);
        assertThat(lots.get(0).getPointsRemaining()).isZero();
        assertThat(lots.get(1).getPointsRemaining()).isEqualTo(700);

        // The invariant still holds after expiry, as it must after every path.
        assertThat(balanceOf(member)).isEqualTo(livePointsOf(member));
    }

    @Test
    @DisplayName("asOf is honoured, so expiry can be evaluated at any point in time")
    void respectsTheAsOfInstant() {
        Member member = givenMember();
        givenLot(member, 400, Instant.now(), daysFromNow(100));

        // Nothing has expired today...
        assertThat(pointExpiryService.expireLotsAsOf(Instant.now()).lotsExpired()).isZero();

        // ...but it has, 200 days from now. Being able to ask that question
        // without waiting 200 days is why asOf is a parameter.
        assertThat(pointExpiryService.expireLotsAsOf(daysFromNow(200)).pointsExpired())
                .isEqualTo(400);
    }

    // ----------------------------------------------------------------- helpers

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static Instant daysFromNow(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private int balanceOf(Member member) {
        return ledgerEntryRepository.balanceOf(member.getId());
    }

    private int livePointsOf(Member member) {
        return pointLotRepository.sumLivePoints(member.getId());
    }

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

    private Member givenMember() {
        return tx.execute(status -> memberService.enrol(
                "Expiry Test", "expiry-" + UUID.randomUUID() + "@example.com"));
    }

    private PointLot givenLot(Member member, int points, Instant earnedAt, Instant expiresAt) {
        return tx.execute(status -> {
            PointLot lot = pointLotRepository.save(
                    PointLot.of(member, null, points, earnedAt, expiresAt));
            ledgerEntryRepository.save(LedgerEntry.of(
                    member, LedgerEntryType.EARN, points, lot, "TEST", null, "seeded lot"));
            return lot;
        });
    }
}
