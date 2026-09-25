package com.bank.exception;

// Check if there is a bad input like a pin that isn't 4 digits
public class ValidationException extends BankingException{
    public ValidationException(String userMessage) {
        super(userMessage);
    }
}
