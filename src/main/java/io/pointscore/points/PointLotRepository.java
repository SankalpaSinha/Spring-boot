package io.pointscore.points;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PointLotRepository extends JpaRepository<PointLot, Long> {

    /**
     * Live lots for a member, soonest-expiring first, <strong>locked for
     * update</strong>.
     *
     * <p>{@code PESSIMISTIC_WRITE} makes Hibernate emit {@code SELECT ... FOR
     * UPDATE}. Postgres then holds those rows until the transaction commits, so
     * a second redemption arriving concurrently blocks here instead of reading
     * the same balance and spending it a second time.
     *
     * <p>Read-then-write without this lock is the classic lost-update bug: both
     * requests see 300 points, both allow a 200-point redemption, and the member
     * ends up at -100.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from PointLot l
            where l.member.id = :memberId and l.pointsRemaining > 0
            order by l.expiresAt asc, l.id asc
            """)
    List<PointLot> lockLiveLotsForMember(Long memberId);

    /** Same ordering, no lock -- for read-only views such as the balance endpoint. */
    @Query("""
            select l from PointLot l
            where l.member.id = :memberId and l.pointsRemaining > 0
            order by l.expiresAt asc, l.id asc
            """)
    List<PointLot> findLiveLotsForMember(Long memberId);

    /** Lots that have points left but whose expiry has passed. Input to the nightly job. */
    @Query("""
            select l from PointLot l
            where l.pointsRemaining > 0 and l.expiresAt <= :asOf
            order by l.expiresAt asc, l.id asc
            """)
    List<PointLot> findExpiredLots(Instant asOf);

    @Query("""
            select coalesce(sum(l.pointsRemaining), 0) from PointLot l
            where l.member.id = :memberId and l.pointsRemaining > 0
            """)
    int sumLivePoints(Long memberId);

    @Query("""
            select coalesce(sum(l.pointsRemaining), 0) from PointLot l
            where l.member.id = :memberId
              and l.pointsRemaining > 0
              and l.expiresAt <= :before
            """)
    int sumPointsExpiringBefore(Long memberId, Instant before);
}
