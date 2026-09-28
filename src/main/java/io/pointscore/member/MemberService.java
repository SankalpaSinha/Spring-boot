package io.pointscore.member;

import io.pointscore.common.ConflictException;
import io.pointscore.common.NotFoundException;
import io.pointscore.tier.Tier;
import io.pointscore.tier.TierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final TierRepository tierRepository;

    public MemberService(MemberRepository memberRepository, TierRepository tierRepository) {
        this.memberRepository = memberRepository;
        this.tierRepository = tierRepository;
    }

    @Transactional
    public Member enrol(String name, String email) {
        Tier startingTier = tierRepository.findAllByOrderBySortOrderAsc().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no tiers configured; V2__seed_reference_data.sql should have created them"));

        Member member = new Member();
        member.setName(name.trim());
        member.setEmail(email.trim());
        member.setTier(startingTier);

        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            // Checking existsByEmail first and returning early would look
            // tidier but is a race: two concurrent sign-ups can both read
            // "no such member" before either inserts. The unique index is the
            // real guard, so the duplicate is caught here, where it is certain.
            throw new ConflictException("EMAIL_ALREADY_ENROLLED",
                    "a member with email " + email + " is already enrolled");
        }
    }

    @Transactional(readOnly = true)
    public Member require(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("member", memberId));
    }
}
