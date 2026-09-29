package io.pointscore.points;

import io.pointscore.common.PageResponse;
import io.pointscore.points.PointsDtos.BalanceResponse;
import io.pointscore.points.PointsDtos.LedgerEntryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/{memberId}")
public class PointsController {

    private final BalanceService balanceService;

    public PointsController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@PathVariable Long memberId) {
        return balanceService.balanceOf(memberId);
    }

    @GetMapping("/ledger")
    public PageResponse<LedgerEntryResponse> ledger(
            @PathVariable Long memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(
                balanceService.ledger(memberId, pageable),
                LedgerEntryResponse::from);
    }
}
