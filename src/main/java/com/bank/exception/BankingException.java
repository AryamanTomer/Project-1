package com.bank.exception;

//These are errors we can actually show the user since the getUserMessage() is the line we actually print.
public class BankingException extends RuntimeException{
    private final String userMessage;

    public BankingException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public BankingException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
