package io.pointscore.tier;

import io.pointscore.tier.TierDtos.TierChangeResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Answers "why am I no longer Gold?" -- the question the tier_changes table
 * exists for. Sits under the member prefix, so the self-or-admin rule in
 * SecurityConfig covers it without anything further.
 */
@RestController
@RequestMapping("/api/members/{memberId}/tier")
public class TierController {

    private final TierService tierService;

    public TierController(TierService tierService) {
        this.tierService = tierService;
    }

    /** Transactional because the response reads lazily-loaded tiers. */
    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<TierChangeResponse> history(@PathVariable Long memberId) {
        return tierService.historyOf(memberId).stream().map(TierChangeResponse::from).toList();
    }
}
