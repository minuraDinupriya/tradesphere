package com.tradesphere.catalog;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record Product(UUID id, UUID organizationId, String sku, String name, String description,
                      String category, String unitOfMeasure, boolean active, long version,
                      Instant createdAt, Instant updatedAt, BigDecimal sellingPrice) { }
