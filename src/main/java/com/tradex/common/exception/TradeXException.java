package com.tradex.common.exception;

public class TradeXException extends RuntimeException {

    private final String errorCode;

    public TradeXException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
    }

    public TradeXException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TradeXException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
