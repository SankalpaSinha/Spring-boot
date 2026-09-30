package io.pointscore.transaction;

import io.pointscore.config.ProgrammeProperties;
import io.pointscore.earning.EarnContext;
import io.pointscore.earning.EarnResult;
import io.pointscore.earning.EarnRule;
import io.pointscore.earning.EarnRuleEngine;
import io.pointscore.earning.EarnRuleRepository;
import io.pointscore.member.Member;
import io.pointscore.member.MemberService;
import io.pointscore.points.LedgerEntry;
import io.pointscore.points.LedgerEntryRepository;
import io.pointscore.points.LedgerEntryType;
import io.pointscore.points.PointLot;
import io.pointscore.points.PointLotRepository;
import io.pointscore.tier.TierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns purchases into points.
 *
 * <p>The whole method runs in one database transaction. That is the point: a
 * purchase must either produce a transaction row, a point lot and a ledger entry
 * together, or produce none of them. A crash halfway through that left a lot
 * without its ledger entry would break the invariant the rest of the system
 * relies on -- that a member's ledger sum always equals their live lot total.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final EarnRuleRepository earnRuleRepository;
    private final PointLotRepository pointLotRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EarnRuleEngine earnRuleEngine;
    private final MemberService memberService;
    private final TierService tierService;
    private final ProgrammeProperties programme;

    public TransactionService(TransactionRepository transactionRepository,
                              EarnRuleRepository earnRuleRepository,
                              PointLotRepository pointLotRepository,
                              LedgerEntryRepository ledgerEntryRepository,
                              EarnRuleEngine earnRuleEngine,
                              MemberService memberService,
                              TierService tierService,
                              ProgrammeProperties programme) {
        this.transactionRepository = transactionRepository;
        this.earnRuleRepository = earnRuleRepository;
        this.pointLotRepository = pointLotRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.earnRuleEngine = earnRuleEngine;
        this.memberService = memberService;
        this.tierService = tierService;
        this.programme = programme;
    }

    /** What happened, including whether this was a retry of a purchase we already had. */
    public record IngestOutcome(Transaction transaction, EarnResult result, boolean duplicate) {
    }

    @Transactional
    public IngestOutcome ingest(Long memberId, String externalRef, BigDecimal amount,
                                String category, Instant occurredAt) {
        Member member = memberService.require(memberId);
        Instant purchasedAt = occurredAt != null ? occurredAt : Instant.now();

        // Fast path for the common retry. This is an optimisation, not the
        // guarantee -- two simultaneous retries can both find nothing here. The
        // unique constraint caught below is what actually makes this safe.
        Optional<Transaction> alreadySeen =
                transactionRepository.findByMemberIdAndExternalRef(memberId, externalRef);
        if (alreadySeen.isPresent()) {
            log.debug("Purchase {} for member {} already ingested; returning the original",
                    externalRef, memberId);
            return new IngestOutcome(alreadySeen.get(), null, true);
        }

        List<EarnRule> rules = earnRuleRepository.findAllByActiveTrueOrderByPriorityAsc();

        EarnResult result = earnRuleEngine.evaluate(new EarnContext(
                amount,
                category,
                purchasedAt,
                programme.zone(),
                member.getTier().getEarnMultiplier(),
                rules));

        Transaction transaction = new Transaction();
        transaction.setMember(member);
        transaction.setExternalRef(externalRef);
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setOccurredAt(purchasedAt);
        transaction.setPointsAwarded(result.points());

        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent retry of the same receipt. The
            // other request has done the work; hand back its result rather than
            // failing the caller, which is what idempotency means.
            log.debug("Concurrent ingest of {} for member {}; returning the winner",
                    externalRef, memberId);
            Transaction winner = transactionRepository
                    .findByMemberIdAndExternalRef(memberId, externalRef)
                    .orElseThrow(() -> ex);
            return new IngestOutcome(winner, null, true);
        }

        if (result.points() > 0) {
            awardPoints(member, transaction, result, purchasedAt);
            // Status follows earnings, so a purchase that crosses a threshold
            // upgrades the member now rather than at tonight's review. The
            // multiplier on THIS purchase used the tier they had when they
            // paid: qualifying for Gold does not re-price the receipt that
            // got them there. Same transaction, so a failed recalculation
            // takes the purchase with it rather than leaving points awarded
            // against a stale tier.
            tierService.recalculate(memberId, Instant.now());
        }

        return new IngestOutcome(transaction, result, false);
    }

    /**
     * Creates the lot and its matching ledger entry.
     *
     * <p>These two writes belong together and must never be separated. The lot
     * is the spendable balance; the ledger entry is the explanation. One without
     * the other is a corrupt account.
     */
    private void awardPoints(Member member, Transaction transaction, EarnResult result, Instant earnedAt) {
        PointLot lot = PointLot.of(
                member,
                transaction,
                result.points(),
                earnedAt,
                programme.expiryFor(earnedAt));
        lot = pointLotRepository.save(lot);

        String appliedRules = result.appliedRules().stream()
                .map(rule -> rule.code())
                .collect(Collectors.joining(", "));

        ledgerEntryRepository.save(LedgerEntry.of(
                member,
                LedgerEntryType.EARN,
                result.points(),
                lot,
                "TRANSACTION",
                transaction.getId(),
                "Earned on purchase %s (%s)".formatted(transaction.getExternalRef(), appliedRules)));

        log.debug("Awarded {} points to member {} for purchase {}",
                result.points(), member.getId(), transaction.getExternalRef());
    }

    @Transactional(readOnly = true)
    public Page<Transaction> history(Long memberId, Pageable pageable) {
        memberService.require(memberId);
        return transactionRepository.findByMemberIdOrderByOccurredAtDesc(memberId, pageable);
    }
}
