package com.tradesphere.inventory;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Carries the stock identity so callers can explain a failure using their own saved product labels. */
public class InsufficientStockException extends InventoryException {
    private final UUID productId;
    public InsufficientStockException(UUID productId) {
        super(HttpStatus.CONFLICT, "Insufficient available stock for product " + productId + ".");
        this.productId = productId;
    }
    public UUID productId() { return productId; }
}
