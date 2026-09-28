package io.pointscore.common;

import org.springframework.http.HttpStatus;

/**
 * 422 rather than 400: the request was well-formed and understood, it just
 * cannot be satisfied against current state.
 */
public class InsufficientPointsException extends ApiException {

    private final int required;
    private final int available;

    public InsufficientPointsException(int required, int available) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_POINTS",
                "needs %d points but only %d are available".formatted(required, available));
        this.required = required;
        this.available = available;
    }

    public int getRequired() {
        return required;
    }

    public int getAvailable() {
        return available;
    }
}
