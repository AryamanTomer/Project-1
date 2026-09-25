package com.bank.exception;

// Database is offline or we couldn't fetch a unique Account ID
public class ServiceUnavailableException extends BankingException{
    public ServiceUnavailableException(Throwable cause) {
        super("Service temporarily unavailable. Please try again later.", cause);
    }
}
