package io.pointscore.tier;

import io.pointscore.member.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One recorded movement between tiers. Append-only, like the points ledger. */
@Entity
@Table(name = "tier_changes")
@Immutable
@Getter
@NoArgsConstructor
public class TierChange {

    public enum Reason {
        /** Assigned on enrolment; there was no previous tier. */
        INITIAL,
        UPGRADE,
        DOWNGRADE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_tier_id")
    private Tier fromTier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_tier_id", nullable = false)
    private Tier toTier;

    @Column(nullable = false)
    private int qualifyingPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }

    public static TierChange of(Member member, Tier from, Tier to, int qualifyingPoints) {
        TierChange change = new TierChange();
        change.member = member;
        change.fromTier = from;
        change.toTier = to;
        change.qualifyingPoints = qualifyingPoints;
        change.reason = from == null
                ? Reason.INITIAL
                : (to.getSortOrder() > from.getSortOrder() ? Reason.UPGRADE : Reason.DOWNGRADE);
        return change;
    }
}
