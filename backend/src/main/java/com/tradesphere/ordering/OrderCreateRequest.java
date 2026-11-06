package com.tradesphere.ordering;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(@NotNull UUID requestId, @NotNull UUID customerId, @NotNull UUID warehouseId,
                                 @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID productId, @NotNull @Positive @Max(9007199254740991L) Long quantity) { }
}
