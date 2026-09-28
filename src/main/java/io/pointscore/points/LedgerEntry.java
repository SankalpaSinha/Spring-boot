package io.pointscore.points;

import io.pointscore.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One immutable fact about a member's points.
 *
 * <p>There is no setter and no balance column anywhere: a balance is
 * {@code SUM(points)} over these rows. That is slower than reading an integer,
 * and it is the right trade -- it makes "why is my balance wrong?" an
 * answerable question, and it makes a wrong balance impossible to cause by a
 * single bad write.
 *
 * <p>{@link Immutable} stops Hibernate even attempting an UPDATE. The database
 * enforces the same rule with a trigger, so the guarantee survives code that
 * never goes through JPA.
 */
@Entity
@Table(name = "ledger_entries")
@Immutable
@Getter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType entryType;

    /** Signed, so a balance is a plain SUM with no per-type branching. */
    @Column(nullable = false)
    private int points;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private PointLot lot;

    private String refType;
    private Long refId;
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public static LedgerEntry of(Member member, LedgerEntryType type, int points,
                                 PointLot lot, String refType, Long refId, String description) {
        if (points == 0) {
            throw new IllegalArgumentException("a ledger entry of zero points is meaningless");
        }
        LedgerEntry entry = new LedgerEntry();
        entry.member = member;
        entry.entryType = type;
        entry.points = points;
        entry.lot = lot;
        entry.refType = refType;
        entry.refId = refId;
        entry.description = description;
        return entry;
    }
}
