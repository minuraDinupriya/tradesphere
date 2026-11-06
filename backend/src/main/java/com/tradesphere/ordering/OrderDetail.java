package com.tradesphere.ordering;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetail(UUID id, UUID requestId, UUID customerId, String customerName,
                          UUID warehouseId, String warehouseName, String warehouseCode, String status,
                          String currency, String totalAmount, Instant createdAt, Instant confirmedAt,
                          UUID createdBy, UUID confirmedBy, List<OrderItem> items) {
    public static OrderDetail of(OrderSummary order, List<OrderItem> items) {
        return new OrderDetail(order.id(), order.requestId(), order.customerId(), order.customerName(),
                order.warehouseId(), order.warehouseName(), order.warehouseCode(), order.status(), order.currency(),
                order.totalAmount(), order.createdAt(), order.confirmedAt(), order.createdBy(), order.confirmedBy(), List.copyOf(items));
    }
}
