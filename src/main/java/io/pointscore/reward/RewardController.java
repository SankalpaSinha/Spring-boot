package io.pointscore.reward;

import io.pointscore.reward.RewardDtos.RewardResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What a member can see: the live catalogue only. */
@RestController
@RequestMapping("/api/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping

    @SecurityRequirements // the catalogue is public
    public List<RewardResponse> catalogue() {
        return rewardService.catalogue().stream().map(RewardResponse::from).toList();
    }

    @GetMapping("/{id}")

    @SecurityRequirements // the catalogue is public
    public RewardResponse get(@PathVariable Long id) {
        return RewardResponse.from(rewardService.require(id));
    }
}
