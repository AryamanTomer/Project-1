package com.bank.exception;

// Zero or negative balance or more than 2 decimal places
public class InvalidAmountException extends BankingException {
    public InvalidAmountException() {
        super("Amount must be greater than zero.");
    }

    public InvalidAmountException(String userMessage) {
        super(userMessage);
    }
}
