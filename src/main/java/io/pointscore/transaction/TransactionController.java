package io.pointscore.transaction;

import io.pointscore.common.PageResponse;
import io.pointscore.transaction.TransactionDtos.IngestPurchaseRequest;
import io.pointscore.transaction.TransactionDtos.TransactionResponse;
import io.pointscore.transaction.TransactionService.IngestOutcome;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members/{memberId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> ingest(@PathVariable Long memberId,
                                                      @Valid @RequestBody IngestPurchaseRequest request) {
        IngestOutcome outcome = transactionService.ingest(
                memberId,
                request.externalRef(),
                request.amount(),
                request.category(),
                request.occurredAt());

        TransactionResponse body =
                TransactionResponse.of(outcome.transaction(), outcome.result(), outcome.duplicate());

        // 200 rather than 201 for a replay: nothing was created this time. The
        // caller still gets the original receipt, which is what makes retrying
        // safe for a till on a flaky connection.
        return ResponseEntity
                .status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(body);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(
            @PathVariable Long memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(
                transactionService.history(memberId, pageable),
                transaction -> TransactionResponse.of(transaction, null, false));
    }
}
