package com.tradex.common.exception;

public class InfrastructureNotReadyException extends TradeXException {

    public InfrastructureNotReadyException(String message) {
        super("INFRASTRUCTURE_NOT_READY", message);
    }
}
