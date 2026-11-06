package com.tradesphere.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,31}", message = "Use 1–32 letters, numbers, dots, hyphens or underscores.") String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 240) String location,
        @NotNull Boolean active,
        @NotNull @PositiveOrZero Long version) {
    public WarehouseValues values() { return new WarehouseValues(code, name, location, active); }
}
