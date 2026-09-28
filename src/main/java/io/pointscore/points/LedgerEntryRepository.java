package io.pointscore.points;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    /**
     * The authoritative balance: replayed from history rather than read from a
     * cached column. Must always equal {@code PointLotRepository.sumLivePoints}
     * for the same member -- there is a test that asserts exactly that.
     */
    @Query("""
            select coalesce(sum(e.points), 0) from LedgerEntry e
            where e.member.id = :memberId
            """)
    int balanceOf(Long memberId);
}
