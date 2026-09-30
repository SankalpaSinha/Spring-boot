package io.pointscore.auth;

import io.pointscore.common.ApiException;
import org.springframework.http.HttpStatus;

/** Raised by the security layer when a request carries no usable token. */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }
}
