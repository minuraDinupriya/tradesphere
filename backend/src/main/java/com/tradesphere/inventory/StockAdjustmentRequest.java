package com.tradesphere.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StockAdjustmentRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID productId,
        @NotNull @PositiveOrZero @Max(StockReceiptRequest.MAX_QUANTITY) Long expectedOnHand,
        @NotNull @PositiveOrZero @Max(StockReceiptRequest.MAX_QUANTITY) Long countedOnHand,
        @NotBlank @Size(max = 500) String reason,
        @NotNull UUID requestId) {

    StockAdjustmentRequest normalized() {
        return new StockAdjustmentRequest(warehouseId, productId, expectedOnHand, countedOnHand, reason.strip(), requestId);
    }

    boolean matches(StockAdjustment adjustment) {
        return warehouseId.equals(adjustment.warehouseId()) && productId.equals(adjustment.productId())
                && expectedOnHand == adjustment.previousOnHand() && countedOnHand == adjustment.countedOnHand()
                && reason.equals(adjustment.reason());
    }
}
