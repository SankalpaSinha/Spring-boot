package io.pointscore.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String what, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " does not exist");
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
