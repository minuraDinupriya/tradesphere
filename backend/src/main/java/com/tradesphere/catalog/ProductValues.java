package com.tradesphere.catalog;

import java.util.Locale;

public record ProductValues(String sku, String name, String description, String category,
                            String unitOfMeasure, boolean active) {
    public ProductValues normalized() {
        return new ProductValues(sku.strip().toUpperCase(Locale.ROOT), name.strip(),
                description == null || description.isBlank() ? null : description.strip(),
                category.strip(), unitOfMeasure.strip(), active);
    }
}
