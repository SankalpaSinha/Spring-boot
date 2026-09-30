package io.pointscore.tier;

import io.pointscore.member.Member;
import io.pointscore.member.MemberRepository;
import io.pointscore.tier.TierService.TierAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Re-evaluates every member's tier.
 *
 * <p>A purchase recalculates the buyer's tier on the spot, so upgrades are
 * immediate. Downgrades are the other half: a member who stops buying never
 * triggers a recalculation, and their tier would stay Gold for ever on
 * earnings that left the window long ago. This sweep is what makes the
 * rolling window actually roll.
 *
 * <p>Deliberately not one transaction. Each member is recalculated in its own
 * (inside {@link TierService#recalculate}), so one bad row cannot roll back a
 * night's work for everyone else, and the sweep never holds a lock across
 * thousands of members. Lives apart from the scheduled job so it can be
 * called from tests with the scheduler switched off.
 */
@Service
public class TierReviewService {

    private static final Logger log = LoggerFactory.getLogger(TierReviewService.class);

    /** Members per page. Bounds memory, not correctness. */
    static final int PAGE_SIZE = 500;

    private final TierService tierService;
    private final MemberRepository memberRepository;

    public TierReviewService(TierService tierService, MemberRepository memberRepository) {
        this.tierService = tierService;
        this.memberRepository = memberRepository;
    }

    public record ReviewOutcome(int reviewed, int upgrades, int downgrades, int failed) {
    }

    public ReviewOutcome reviewAll(Instant asOf) {
        int reviewed = 0, upgrades = 0, downgrades = 0, failed = 0;

        Pageable page = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Page<Member> members;
        do {
            members = memberRepository.findAll(page);
            for (Member member : members) {
                try {
                    TierAssessment assessment = tierService.recalculate(member.getId(), asOf);
                    reviewed++;
                    if (assessment.changed()) {
                        if (assessment.currentTier().getSortOrder() > assessment.previousTier().getSortOrder()) {
                            upgrades++;
                        } else {
                            downgrades++;
                        }
                    }
                } catch (RuntimeException ex) {
                    // Logged and skipped, not rethrown: the other members still
                    // deserve their review tonight. The count surfaces it.
                    failed++;
                    log.error("Tier review failed for member {}", member.getId(), ex);
                }
            }
            page = members.nextPageable();
        } while (members.hasNext());

        return new ReviewOutcome(reviewed, upgrades, downgrades, failed);
    }
}
