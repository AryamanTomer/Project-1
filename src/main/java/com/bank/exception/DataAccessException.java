package com.bank.exception;

// JDBC blew up and the service is not available
public class DataAccessException extends RuntimeException{
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
