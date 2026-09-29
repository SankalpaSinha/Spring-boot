package io.pointscore.reward;

import io.pointscore.common.ConflictException;
import io.pointscore.common.NotFoundException;
import io.pointscore.reward.RewardDtos.CreateRewardRequest;
import io.pointscore.reward.RewardDtos.UpdateRewardRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RewardService {

    private final RewardRepository rewardRepository;

    public RewardService(RewardRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    @Transactional(readOnly = true)
    public List<Reward> catalogue() {
        return rewardRepository.findByActiveTrueOrderByCostPointsAsc();
    }

    @Transactional(readOnly = true)
    public List<Reward> all() {
        return rewardRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Reward require(Long rewardId) {
        return rewardRepository.findById(rewardId)
                .orElseThrow(() -> new NotFoundException("reward", rewardId));
    }

    @Transactional
    public Reward create(CreateRewardRequest request) {
        Reward reward = new Reward();
        reward.setCode(request.code().trim().toUpperCase());
        reward.setName(request.name().trim());
        reward.setDescription(request.description());
        reward.setCostPoints(request.costPoints());
        reward.setStock(request.stock());

        try {
            return rewardRepository.saveAndFlush(reward);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("REWARD_CODE_TAKEN",
                    "a reward with code " + reward.getCode() + " already exists");
        }
    }

    /** Partial update: a null field means "leave it alone", not "set it to null". */
    @Transactional
    public Reward update(Long rewardId, UpdateRewardRequest request) {
        Reward reward = require(rewardId);

        if (request.name() != null) {
            reward.setName(request.name().trim());
        }
        if (request.description() != null) {
            reward.setDescription(request.description());
        }
        if (request.costPoints() != null) {
            reward.setCostPoints(request.costPoints());
        }
        if (request.stock() != null) {
            reward.setStock(request.stock());
        }
        if (request.active() != null) {
            reward.setActive(request.active());
        }
        return rewardRepository.save(reward);
    }
}
