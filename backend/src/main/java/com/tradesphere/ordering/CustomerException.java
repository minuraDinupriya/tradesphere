package com.tradesphere.ordering;

import org.springframework.http.HttpStatus;

public class CustomerException extends RuntimeException {
    private final HttpStatus status;
    public CustomerException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
