package com.tradex.common.exception;

public class BusinessRuleViolationException extends TradeXException {

    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
