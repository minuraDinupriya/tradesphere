package com.tradesphere.inventory;

import org.springframework.http.HttpStatus;

public class InventoryException extends RuntimeException {
    private final HttpStatus status;
    public InventoryException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
