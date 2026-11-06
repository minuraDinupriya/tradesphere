package com.tradesphere.ordering;

import java.util.UUID;

public record OrderItem(UUID productId, String sku, String productName, String unitOfMeasure,
                        long quantity, String unitPrice, String lineTotal) { }
