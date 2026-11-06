package com.tradesphere.inventory;

import java.util.Locale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseValues(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,31}", message = "Use 1–32 letters, numbers, dots, hyphens or underscores.") String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 240) String location,
        @NotNull Boolean active) {
    public WarehouseValues normalized() {
        return new WarehouseValues(code.strip().toUpperCase(Locale.ROOT), name.strip(),
                location == null || location.isBlank() ? null : location.strip(), active);
    }
}
