package com.tradesphere.catalog;

import java.util.UUID;

public record SellingPrice(UUID productId, String currency, String amount, long version) { }
