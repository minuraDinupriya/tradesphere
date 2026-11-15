package com.tradesphere.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockAdjustment(UUID id, UUID warehouseId, UUID productId, long previousOnHand,
                              long countedOnHand, long reserved, String reason, String actorUsername,
                              Instant occurredAt) { }
