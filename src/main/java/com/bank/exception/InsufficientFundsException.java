package com.bank.exception;

//Would go below $0.00
public class InsufficientFundsException extends BankingException{
    public InsufficientFundsException() {
        super("Insufficient funds. This account cannot be overdrawn");
    }
}
