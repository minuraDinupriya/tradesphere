package com.tradesphere.inventory;

import java.util.List;

public record InventoryPage<T>(List<T> items, long totalElements, int page, int size, long totalPages) {
    public static <T> InventoryPage<T> of(List<T> items, long total, int page, int size) {
        return new InventoryPage<>(items, total, page, size, (total + size - 1) / size);
    }
}
