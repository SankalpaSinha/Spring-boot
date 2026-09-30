package io.pointscore.auth;

import io.pointscore.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * One message for "no such account" and "wrong password". Distinguishing them
 * would let anyone enumerate which addresses are enrolled.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "email or password is incorrect");
    }
}
