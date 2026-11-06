package com.tradesphere.inventory;

import java.time.Instant;
import java.util.UUID;

public record OrderInventoryMovement(UUID id, UUID orderId, UUID warehouseId, UUID productId, String sku, String productName,
                                     String kind, long quantity, long onHandDelta, long reservedDelta,
                                     UUID actorId, String actorUsername, Instant occurredAt) { }
