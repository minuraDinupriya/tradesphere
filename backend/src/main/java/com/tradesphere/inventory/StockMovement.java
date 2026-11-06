package com.tradesphere.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockMovement(UUID id, UUID requestId, UUID warehouseId, String warehouseCode, String warehouseName,
                            UUID productId, String sku, String productName, String unitOfMeasure, long quantity,
                            String reference, String note, UUID receivedBy, String receivedByUsername, Instant receivedAt) { }
