package com.tradesphere.catalog;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellingPriceService {
    private final SellingPriceRepository prices;

    public SellingPriceService(SellingPriceRepository prices) { this.prices = prices; }

    @Transactional(readOnly = true)
    public SellingPrice get(UUID organizationId, UUID productId) {
        return prices.find(organizationId, productId).orElseThrow(ProductNotFoundException::new);
    }

    @Transactional
    public SellingPrice update(UUID organizationId, UUID productId, String amount, long version) {
        var price = SellingPriceAmount.parse(amount);
        if (!prices.update(organizationId, productId, price, version)) {
            get(organizationId, productId);
            throw new ProductConflictException("This selling price changed. Reload the latest price before saving again.");
        }
        return get(organizationId, productId);
    }

}
