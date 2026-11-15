package com.tradesphere.inventory;

import com.tradesphere.catalog.CatalogProductAccess;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentService {
    private final StockAdjustmentRepository adjustments;
    private final InventoryRepository inventory;
    private final WarehouseRepository warehouses;
    private final CatalogProductAccess products;

    StockAdjustmentService(StockAdjustmentRepository adjustments, InventoryRepository inventory,
                           WarehouseRepository warehouses, CatalogProductAccess products) {
        this.adjustments = adjustments;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.products = products;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdjustmentHistory history(UUID organizationId, UUID warehouseId, UUID productId, int page, int size) {
        var balance = inventory.balances(organizationId, "", warehouseId, productId, 0, 1)
                .stream().findFirst().orElseThrow(StockAdjustmentService::balanceNotFound);
        var items = adjustments.history(organizationId, warehouseId, productId, page, size);
        var count = adjustments.count(organizationId, warehouseId, productId);
        return new AdjustmentHistory(balance, InventoryPage.of(items, count, page, size));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdjustmentResult adjust(UUID organizationId, UUID actorId, StockAdjustmentRequest input) {
        var request = input.normalized();
        var existing = adjustments.findByRequest(organizationId, request.requestId());
        if (existing.isPresent()) return replay(request, existing.get());

        // Match the warehouse -> product -> balance lock order used by receipts and order confirmation.
        var warehouse = warehouses.find(organizationId, request.warehouseId(), true)
                .orElseThrow(StockAdjustmentService::balanceNotFound);
        boolean productActive = products.lockActiveStatus(organizationId, request.productId());
        var balance = adjustments.lockBalance(organizationId, request.warehouseId(), request.productId())
                .orElseThrow(StockAdjustmentService::balanceNotFound);

        // A same-balance request could have committed while this transaction waited for its lock.
        existing = adjustments.findByRequest(organizationId, request.requestId());
        if (existing.isPresent()) return replay(request, existing.get());

        if (!warehouse.active() || !productActive) {
            throw new InventoryException(HttpStatus.CONFLICT, "Stock adjustments require an active warehouse and product.");
        }
        if (request.expectedOnHand() != balance.onHand()) {
            throw new InventoryException(HttpStatus.CONFLICT, "On-hand stock changed. Reload the balance and verify your count before trying again.");
        }
        if (request.countedOnHand() == balance.onHand()) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "The counted quantity must differ from the current on-hand quantity.");
        }
        if (request.countedOnHand() < balance.reserved()) {
            throw new InventoryException(HttpStatus.CONFLICT, "The counted quantity cannot be below the reserved quantity.");
        }

        // Claim the organization-wide request ID before writing stock, including for concurrent requests on different balances.
        if (!adjustments.insert(organizationId, actorId, request, balance)) {
            return replay(request, adjustments.findByRequest(organizationId, request.requestId()).orElseThrow());
        }
        adjustments.updateOnHand(organizationId, request);
        return new AdjustmentResult(adjustments.findByRequest(organizationId, request.requestId()).orElseThrow(), false);
    }

    private static AdjustmentResult replay(StockAdjustmentRequest request, StockAdjustment existing) {
        if (!request.matches(existing)) {
            throw new InventoryException(HttpStatus.CONFLICT, "This request ID was already used for a different stock adjustment.");
        }
        return new AdjustmentResult(existing, true);
    }

    private static InventoryException balanceNotFound() {
        return new InventoryException(HttpStatus.NOT_FOUND, "Inventory balance not found in your organization.");
    }

    public record AdjustmentResult(StockAdjustment adjustment, boolean replayed) { }

    public record AdjustmentHistory(InventoryBalance balance, InventoryPage<StockAdjustment> history) { }
}
