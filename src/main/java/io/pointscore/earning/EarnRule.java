package io.pointscore.earning;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "earn_rules")
@Getter
@Setter
@NoArgsConstructor
public class EarnRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EarnRuleType ruleType;

    /**
     * Type-specific parameters, stored as jsonb. Mapped as a Map rather than a
     * String so callers get parsed values instead of re-parsing JSON by hand;
     * each {@link EarnRuleType} documents the shape it expects.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditions = new HashMap<>();

    @Column(nullable = false)
    private BigDecimal multiplier;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active = true;

    private Instant validFrom;
    private Instant validTo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Whether this rule is live at the given moment. Kept on the entity so the
     * date-window logic exists once, rather than being re-expressed in every
     * query and test that needs it.
     */
    public boolean isLiveAt(Instant when) {
        if (!active) {
            return false;
        }
        if (validFrom != null && when.isBefore(validFrom)) {
            return false;
        }
        // Exclusive upper bound: a rule valid_to midnight stops at midnight.
        return validTo == null || when.isBefore(validTo);
    }
}
