package io.pointscore.tier;

import io.pointscore.config.ProgrammeProperties;
import io.pointscore.member.Member;
import io.pointscore.member.MemberRepository;
import io.pointscore.member.MemberService;
import io.pointscore.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Decides which tier a member belongs in.
 *
 * <p>Qualification is by points <em>earned</em> over a rolling twelve months,
 * never by current balance. This distinction is the whole design: if spending
 * points could demote you, the programme would punish the exact behaviour it
 * exists to encourage, and members would hoard points rather than redeem them.
 * Every real programme separates "status" from "currency" for this reason.
 */
@Service
public class TierService {

    private static final Logger log = LoggerFactory.getLogger(TierService.class);

    private final TierRepository tierRepository;
    private final TierChangeRepository tierChangeRepository;
    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final ProgrammeProperties programme;

    public TierService(TierRepository tierRepository,
                       TierChangeRepository tierChangeRepository,
                       TransactionRepository transactionRepository,
                       MemberRepository memberRepository,
                       MemberService memberService,
                       ProgrammeProperties programme) {
        this.tierRepository = tierRepository;
        this.tierChangeRepository = tierChangeRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.programme = programme;
    }

    /** What the recalculation decided. */
    public record TierAssessment(Tier previousTier, Tier currentTier, int qualifyingPoints, boolean changed) {
    }

    /**
     * Recalculates a member's tier as of a given moment.
     *
     * @param asOf injected rather than read from the clock, so a test can ask
     *             "what would this member's tier be in eight months?" without
     *             waiting eight months
     */
    @Transactional
    public TierAssessment recalculate(Long memberId, Instant asOf) {
        // ------------------------------------------------------------------
        // YOUR TASK (milestone 4, part 2)
        //
        //   1. Member member = memberService.require(memberId);
        //      Tier currentTier = member.getTier();
        //
        //   2. Work out the start of the rolling window:
        //        Instant windowStart = programme.qualificationWindowStart(asOf);
        //      (already written -- it steps back twelve months through the
        //      programme's calendar, same reasoning as expiry dates)
        //
        //   3. int qualifyingPoints =
        //        transactionRepository.sumPointsAwardedSince(memberId, windowStart);
        //
        //      Note this sums points AWARDED on purchases, not the member's
        //      balance. Redeeming a reward must never cost someone their
        //      status -- see the class javadoc.
        //
        //   4. Tier earnedTier = tierRepository.findHighestQualifying(qualifyingPoints)
        //          .orElse(currentTier);
        //      That query returns the best tier whose min_points_12m is at or
        //      below the figure.
        //
        //   5. If earnedTier has the same id as currentTier, nothing changed:
        //        return new TierAssessment(currentTier, currentTier,
        //                                  qualifyingPoints, false);
        //
        //   6. Otherwise: set the member's tier, save the member, and record
        //      the movement:
        //        tierChangeRepository.save(
        //            TierChange.of(member, currentTier, earnedTier, qualifyingPoints));
        //      TierChange.of works out UPGRADE vs DOWNGRADE from sort_order,
        //      so you do not need to.
        //
        //      Then return the assessment with changed = true.
        //
        // The test that matters most is the one proving a member who spends
        // their entire balance keeps their tier. If that fails, step 3 is
        // reading the balance instead of earnings.
        // ------------------------------------------------------------------
        throw new UnsupportedOperationException(
                "milestone 4: implement TierService.recalculate");
    }

    public TierAssessment recalculateNow(Long memberId) {
        return recalculate(memberId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TierChange> historyOf(Long memberId) {
        memberService.require(memberId);
        return tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(memberId);
    }
}
