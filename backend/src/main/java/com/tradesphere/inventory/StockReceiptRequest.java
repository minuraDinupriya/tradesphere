package com.tradesphere.inventory;

import java.util.Objects;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockReceiptRequest(@NotNull UUID requestId, @NotNull UUID warehouseId, @NotNull UUID productId,
                                  @NotNull @Positive @Max(9007199254740991L) Long quantity,
                                  @Size(max = 120) String reference, @Size(max = 500) String note) {
    public static final long MAX_QUANTITY = 9007199254740991L;
    public StockReceiptRequest normalized() {
        return new StockReceiptRequest(requestId, warehouseId, productId, quantity, clean(reference), clean(note));
    }
    public boolean matches(StockMovement movement) {
        return warehouseId.equals(movement.warehouseId()) && productId.equals(movement.productId())
                && quantity == movement.quantity() && Objects.equals(reference, movement.reference()) && Objects.equals(note, movement.note());
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
