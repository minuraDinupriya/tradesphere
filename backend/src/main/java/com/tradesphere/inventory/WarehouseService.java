package com.tradesphere.inventory;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {
    private final WarehouseRepository warehouses;
    public WarehouseService(WarehouseRepository warehouses) { this.warehouses = warehouses; }

    @Transactional
    public Warehouse create(UUID organizationId, WarehouseValues values) {
        try { return warehouses.create(organizationId, values.normalized()); }
        catch (DuplicateKeyException exception) { throw new InventoryException(HttpStatus.CONFLICT, "That warehouse code already exists in your organization."); }
    }

    @Transactional(readOnly = true)
    public Warehouse get(UUID organizationId, UUID id) {
        return warehouses.find(organizationId, id, false).orElseThrow(WarehouseService::notFound);
    }

    @Transactional
    public Warehouse update(UUID organizationId, UUID id, WarehouseValues values, long version) {
        try {
            return warehouses.update(organizationId, id, values.normalized(), version).orElseGet(() -> {
                get(organizationId, id);
                throw new InventoryException(HttpStatus.CONFLICT, "This warehouse changed. Reload its latest version before saving again.");
            });
        } catch (DuplicateKeyException exception) { throw new InventoryException(HttpStatus.CONFLICT, "That warehouse code already exists in your organization."); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryPage<Warehouse> list(UUID organizationId, String q, Boolean active, int page, int size) {
        return InventoryPage.of(warehouses.list(organizationId, q.strip(), active, page, size),
                warehouses.count(organizationId, q.strip(), active), page, size);
    }

    static InventoryException notFound() { return new InventoryException(HttpStatus.NOT_FOUND, "Warehouse not found in your organization."); }
}
