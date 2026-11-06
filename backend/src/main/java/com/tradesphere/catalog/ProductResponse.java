package com.tradesphere.catalog;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, String description, String category,
                              String unitOfMeasure, boolean active, long version, Instant createdAt, Instant updatedAt, String sellingPrice) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.sku(), product.name(), product.description(), product.category(),
                product.unitOfMeasure(), product.active(), product.version(), product.createdAt(), product.updatedAt(),
                product.sellingPrice() == null ? null : product.sellingPrice().toPlainString());
    }
}
