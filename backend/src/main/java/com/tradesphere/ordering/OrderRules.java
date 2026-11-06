package com.tradesphere.ordering;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class OrderRules {
    private OrderRules() { }

    public static List<OrderCreateRequest.Item> canonicalItems(List<OrderCreateRequest.Item> items) {
        if (items == null || items.isEmpty() || items.size() > 100) throw invalid("An order needs between 1 and 100 items.");
        var products = new HashSet<UUID>();
        for (var item : items) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0 || item.quantity() > 9007199254740991L) {
                throw invalid("Order items need a product and a positive whole-unit quantity within the supported range.");
            }
            if (!products.add(item.productId())) throw invalid("Choose each product only once; change its quantity instead.");
        }
        // Text UUID order agrees with PostgreSQL UUID order, including UUIDs with the high bit set.
        return items.stream().sorted(Comparator.comparing(item -> item.productId().toString())).toList();
    }

    public static BigDecimal total(List<OrderItem> items) {
        return items.stream().map(item -> new BigDecimal(item.unitPrice()).multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    private static OrderException invalid(String message) { return new OrderException(HttpStatus.BAD_REQUEST, message); }
}
