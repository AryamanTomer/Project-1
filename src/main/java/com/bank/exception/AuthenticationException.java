package com.bank.exception;

// This checks for the wrong ID or wrong PIN
public class AuthenticationException extends BankingException{
    public AuthenticationException() {
        super("Incorrect Account ID or Pin. Please try again");
    }
}
