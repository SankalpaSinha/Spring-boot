package io.pointscore.tier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A membership level. Qualification is by points <em>earned</em> in a rolling
 * twelve months, never by current balance -- see {@code min_points_12m} in the
 * schema for why.
 */
@Entity
@Table(name = "tiers")
@Getter
@Setter
@NoArgsConstructor
public class Tier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Explicit name: the default naming strategy would produce min_points12m. */
    @Column(name = "min_points_12m", nullable = false)
    private int minPoints12m;

    @Column(nullable = false)
    private BigDecimal earnMultiplier;

    @Column(nullable = false)
    private int sortOrder;
}
