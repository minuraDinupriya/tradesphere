package com.tradesphere.inventory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.tradesphere.catalog.CatalogProductAccess;

@Service
public class InventoryService {
    private final InventoryRepository inventory;
    private final WarehouseRepository warehouses;
    private final CatalogProductAccess products;
    public InventoryService(InventoryRepository inventory, WarehouseRepository warehouses, CatalogProductAccess products) {
        this.inventory = inventory; this.warehouses = warehouses; this.products = products;
    }

    @Transactional
    public ReceiptResult receive(UUID organizationId, UUID actorId, StockReceiptRequest input) {
        var request = input.normalized();
        var existing = inventory.receiptByRequest(organizationId, request.requestId());
        if (existing.isPresent()) return replay(request, existing.get());
        // Consistent warehouse-then-product locking prevents deactivation during a new receipt.
        var warehouse = warehouses.find(organizationId, request.warehouseId(), true).orElseThrow(WarehouseService::notFound);
        if (!warehouse.active()) throw new InventoryException(HttpStatus.CONFLICT, "Stock can only be received into an active warehouse.");
        if (!products.lockActiveStatus(organizationId, request.productId())) {
            throw new InventoryException(HttpStatus.CONFLICT, "Stock can only be received for an active product.");
        }
        var inserted = inventory.insertReceipt(organizationId, actorId, request);
        if (inserted.isEmpty()) {
            // ON CONFLICT waits for the competing transaction; this READ COMMITTED statement sees its committed receipt.
            return replay(request, inventory.receiptByRequest(organizationId, request.requestId()).orElseThrow());
        }
        if (!inventory.increaseBalance(organizationId, request)) {
            throw new InventoryException(HttpStatus.CONFLICT, "This receipt would exceed the supported inventory quantity range.");
        }
        return new ReceiptResult(inventory.movement(organizationId, inserted.get()).orElseThrow(), false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryPage<InventoryBalance> balances(UUID organizationId, String q, UUID warehouseId, UUID productId, int page, int size) {
        return InventoryPage.of(inventory.balances(organizationId, q.strip(), warehouseId, productId, page, size),
                inventory.balanceCount(organizationId, q.strip(), warehouseId, productId), page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryPage<StockMovement> movements(UUID organizationId, String q, UUID warehouseId, UUID productId, int page, int size) {
        return InventoryPage.of(inventory.movements(organizationId, q.strip(), warehouseId, productId, page, size),
                inventory.movementCount(organizationId, q.strip(), warehouseId, productId), page, size);
    }

    @Transactional(readOnly = true)
    public StockMovement movement(UUID organizationId, UUID id) {
        return inventory.movement(organizationId, id).orElseThrow(() -> new InventoryException(HttpStatus.NOT_FOUND, "Stock movement not found in your organization."));
    }

    private ReceiptResult replay(StockReceiptRequest request, StockMovement movement) {
        if (!request.matches(movement)) throw new InventoryException(HttpStatus.CONFLICT, "This request ID was already used for a different receipt. Start a new receipt.");
        return new ReceiptResult(movement, true);
    }
    public record ReceiptResult(StockMovement movement, boolean replayed) { }
}
