package com.tradex.common.exception;

public class InvalidCredentialsException extends TradeXException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super("INVALID_CREDENTIALS", message);
    }
}
