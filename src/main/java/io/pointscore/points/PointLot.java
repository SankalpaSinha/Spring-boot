package io.pointscore.points;

import io.pointscore.member.Member;
import io.pointscore.transaction.Transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A dated bucket of points from a single earning event.
 *
 * <p>Points are not a single pooled number, because expiry is per-batch: points
 * earned in January die in January, whatever you earned since. A lot therefore
 * remembers both what it started with and what is left of it.
 */
@Entity
@Table(name = "point_lots")
@Getter
@Setter
@NoArgsConstructor
public class PointLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** Null when the lot came from a manual adjustment rather than a purchase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(nullable = false, updatable = false)
    private int pointsEarned;

    /**
     * The only mutable quantity in the system. Anything that changes it must
     * write a matching ledger row in the same database transaction.
     */
    @Column(nullable = false)
    private int pointsRemaining;

    @Column(nullable = false, updatable = false)
    private Instant earnedAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    public static PointLot of(Member member, Transaction transaction, int points,
                              Instant earnedAt, Instant expiresAt) {
        PointLot lot = new PointLot();
        lot.member = member;
        lot.transaction = transaction;
        lot.pointsEarned = points;
        lot.pointsRemaining = points;
        lot.earnedAt = earnedAt;
        lot.expiresAt = expiresAt;
        return lot;
    }

    public boolean isExhausted() {
        return pointsRemaining == 0;
    }

    /**
     * Draws points out of this lot, returning how many were actually taken --
     * which may be fewer than asked for, when the lot runs dry mid-redemption
     * and the caller must move on to the next one.
     */
    public int draw(int wanted) {
        if (wanted <= 0) {
            throw new IllegalArgumentException("wanted must be positive, was " + wanted);
        }
        int taken = Math.min(wanted, pointsRemaining);
        pointsRemaining -= taken;
        return taken;
    }
}
