package com.tradesphere.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}",
                message = "Use 1–64 letters, numbers, dots, hyphens or underscores; start with a letter or number.") String sku,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 32) String unitOfMeasure,
        @NotNull Boolean active,
        @NotNull @PositiveOrZero Long version) {
    public ProductValues values() { return new ProductValues(sku, name, description, category, unitOfMeasure, active); }
}
