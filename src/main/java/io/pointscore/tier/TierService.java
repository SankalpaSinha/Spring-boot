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
        Member member = memberService.require(memberId);
        // Loaded for real rather than taken as the member's lazy proxy: the
        // assessment outlives this transaction, and callers read sortOrder
        // and name from it (the review job counts upgrades against downgrades).
        Tier currentTier = tierRepository.findById(member.getTier().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "member " + memberId + " has tier_id " + member.getTier().getId() + " which does not exist"));

        Instant windowStart = programme.qualificationWindowStart(asOf);
        int qualifyingPoints = transactionRepository.sumPointsAwardedSince(memberId, windowStart);

        Tier earnedTier = tierRepository.findHighestQualifying(qualifyingPoints)
                .orElse(currentTier);

        if (earnedTier.getId().equals(currentTier.getId())) {
            return new TierAssessment(currentTier, currentTier, qualifyingPoints, false);
        }

        member.setTier(earnedTier);
        memberRepository.save(member);
        tierChangeRepository.save(TierChange.of(member, currentTier, earnedTier, qualifyingPoints));

        log.info("Member {} moved from {} to {} on {} qualifying points",
                memberId, currentTier.getName(), earnedTier.getName(), qualifyingPoints);
        return new TierAssessment(currentTier, earnedTier, qualifyingPoints, true);
    }

    /** Same self-invocation caveat as {@code PointExpiryService.expireNow}. */
    @Transactional
    public TierAssessment recalculateNow(Long memberId) {
        return recalculate(memberId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TierChange> historyOf(Long memberId) {
        memberService.require(memberId);
        return tierChangeRepository.findByMemberIdOrderByChangedAtDescIdDesc(memberId);
    }
}
