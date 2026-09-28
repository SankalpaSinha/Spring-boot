package io.pointscore.earning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EarnRuleRepository extends JpaRepository<EarnRule, Long> {

    Optional<EarnRule> findByCode(String code);

    /**
     * Every active rule, lowest priority value first.
     *
     * <p>The date window is deliberately <em>not</em> filtered here. The engine
     * evaluates rules as of the purchase's {@code occurredAt}, not as of now --
     * a receipt that arrives late must still earn at the rate that applied when
     * it was rung up. A {@code where now() between valid_from and valid_to}
     * query would quietly get that wrong.
     */
    List<EarnRule> findAllByActiveTrueOrderByPriorityAsc();
}
