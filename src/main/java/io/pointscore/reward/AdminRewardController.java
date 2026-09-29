package io.pointscore.reward;

import io.pointscore.reward.RewardDtos.CreateRewardRequest;
import io.pointscore.reward.RewardDtos.RewardResponse;
import io.pointscore.reward.RewardDtos.UpdateRewardRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Administrative reward management.
 *
 * <p>Separated from {@link RewardController} by URL rather than by method, so
 * that milestone 4 can lock the whole {@code /api/admin/**} tree down to
 * ROLE_ADMIN in one rule instead of annotating endpoints individually and
 * hoping none were missed.
 */
@RestController
@RequestMapping("/api/admin/rewards")
public class AdminRewardController {

    private final RewardService rewardService;

    public AdminRewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    /** Includes inactive rewards, which the member-facing catalogue hides. */
    @GetMapping
    public List<RewardResponse> all() {
        return rewardService.all().stream().map(RewardResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RewardResponse> create(@Valid @RequestBody CreateRewardRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Reward reward = rewardService.create(request);
        URI location = uriBuilder.path("/api/rewards/{id}").buildAndExpand(reward.getId()).toUri();
        return ResponseEntity.created(location).body(RewardResponse.from(reward));
    }

    @PatchMapping("/{id}")
    public RewardResponse update(@PathVariable Long id,
                                 @Valid @RequestBody UpdateRewardRequest request) {
        return RewardResponse.from(rewardService.update(id, request));
    }
}
