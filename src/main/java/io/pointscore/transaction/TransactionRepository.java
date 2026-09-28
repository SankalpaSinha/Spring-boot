package io.pointscore.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByMemberIdAndExternalRef(Long memberId, String externalRef);

    Page<Transaction> findByMemberIdOrderByOccurredAtDesc(Long memberId, Pageable pageable);

    /**
     * Points earned since a cut-off -- the input to tier qualification.
     * COALESCE keeps the return non-null for a member who has earned nothing.
     */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(t.pointsAwarded), 0) from Transaction t
            where t.member.id = :memberId and t.occurredAt >= :since
            """)
    int sumPointsAwardedSince(Long memberId, Instant since);
}
