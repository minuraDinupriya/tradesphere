package com.tradesphere.ordering;

import java.time.Instant;
import java.util.UUID;

public record OrderSummary(UUID id, UUID requestId, UUID customerId, String customerName,
                           UUID warehouseId, String warehouseName, String warehouseCode, String status,
                           String currency, String totalAmount, Instant createdAt, Instant confirmedAt,
                           UUID createdBy, UUID confirmedBy) { }
