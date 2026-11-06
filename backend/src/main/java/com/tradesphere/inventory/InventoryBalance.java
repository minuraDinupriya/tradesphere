package com.tradesphere.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryBalance(UUID warehouseId, String warehouseCode, String warehouseName,
                               UUID productId, String sku, String productName, String unitOfMeasure,
                               long onHand, long reserved, long available, boolean warehouseActive,
                               boolean productActive, Instant updatedAt) { }
