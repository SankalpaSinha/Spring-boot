package io.pointscore.points;

import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import io.pointscore.points.PointsDtos.BalanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class BalanceService {

    /** How far ahead "expiring soon" looks. Long enough for a member to act on. */
    private static final Duration EXPIRY_WARNING_WINDOW = Duration.ofDays(30);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PointLotRepository pointLotRepository;
    private final MemberService memberService;

    public BalanceService(LedgerEntryRepository ledgerEntryRepository,
                          PointLotRepository pointLotRepository,
                          MemberService memberService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.pointLotRepository = pointLotRepository;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balanceOf(Long memberId) {
        Member member = memberService.require(memberId);
        Instant now = Instant.now();

        // The ledger is the authority. Lots are queried only for the expiry
        // view -- which is the one question the ledger cannot answer, since it
        // records what happened rather than what is still spendable.
        int balance = ledgerEntryRepository.balanceOf(memberId);

        int expiringSoon = pointLotRepository.sumPointsExpiringBefore(
                memberId, now.plus(EXPIRY_WARNING_WINDOW));

        List<PointLot> liveLots = pointLotRepository.findLiveLotsForMember(memberId);
        Instant nextExpiryAt = liveLots.isEmpty() ? null : liveLots.getFirst().getExpiresAt();

        return new BalanceResponse(
                member.getId(),
                member.getTier().getCode(),
                member.getTier().getName(),
                balance,
                expiringSoon,
                nextExpiryAt,
                ledgerEntryRepository.lifetimeEarnedBy(memberId));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> ledger(Long memberId, Pageable pageable) {
        memberService.require(memberId);
        return ledgerEntryRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable);
    }
}
