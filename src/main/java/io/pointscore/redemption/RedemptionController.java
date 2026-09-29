package io.pointscore.redemption;

import io.pointscore.common.PageResponse;
import io.pointscore.redemption.RedemptionDtos.RedeemRequest;
import io.pointscore.redemption.RedemptionDtos.RedemptionResponse;
import io.pointscore.redemption.RedemptionService.RedemptionOutcome;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/members/{memberId}/redemptions")
public class RedemptionController {

    private final RedemptionService redemptionService;

    public RedemptionController(RedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    /**
     * @param idempotencyKey optional but strongly recommended. A client that
     *                       omits it gets a generated one, which makes the call
     *                       safe against nothing -- a retry without the original
     *                       key is indistinguishable from a second redemption,
     *                       and will spend again.
     */
    @PostMapping
    public ResponseEntity<RedemptionResponse> redeem(
            @PathVariable Long memberId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RedeemRequest request) {

        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();

        RedemptionOutcome outcome = redemptionService.redeem(memberId, request.rewardId(), key);
        int balanceAfter = redemptionService.balanceOf(memberId);

        return ResponseEntity
                .status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(RedemptionResponse.of(outcome.redemption(), balanceAfter, outcome.duplicate()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<RedemptionResponse> history(
            @PathVariable Long memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(
                redemptionService.history(memberId, pageable),
                redemption -> RedemptionResponse.of(redemption, 0, false));
    }
}
