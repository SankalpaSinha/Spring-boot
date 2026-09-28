package io.pointscore.common;

import org.springframework.http.HttpStatus;

public class OutOfStockException extends ApiException {

    public OutOfStockException(String rewardName) {
        super(HttpStatus.CONFLICT, "OUT_OF_STOCK", rewardName + " is out of stock");
    }
}
