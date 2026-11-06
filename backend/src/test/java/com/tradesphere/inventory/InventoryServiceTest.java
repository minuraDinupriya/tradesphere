package com.tradesphere.inventory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.tradesphere.catalog.CatalogProductAccess;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private InventoryRepository inventory;
    @Mock private WarehouseRepository warehouses;
    @Mock private CatalogProductAccess products;
    private InventoryService service;
    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final StockReceiptRequest request = new StockReceiptRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12L, " DOC-1 ", "  ");

    @BeforeEach
    void setUp() { service = new InventoryService(inventory, warehouses, products); }

    @Test
    void normalizedMatchingRetryReturnsOriginalWithoutTouchingStockOrEligibility() {
        var movement = movement(12);
        when(inventory.receiptByRequest(organizationId, request.requestId())).thenReturn(Optional.of(movement));
        var result = service.receive(organizationId, actorId, request);
        assertThat(result.replayed()).isTrue();
        assertThat(result.movement()).isSameAs(movement);
        verifyNoInteractions(warehouses, products);
        verify(inventory, never()).increaseBalance(any(), any());
    }

    @Test
    void sameRequestIdCannotBeReusedForDifferentQuantity() {
        when(inventory.receiptByRequest(organizationId, request.requestId())).thenReturn(Optional.of(movement(13)));
        assertThatThrownBy(() -> service.receive(organizationId, actorId, request)).isInstanceOf(InventoryException.class).hasMessageContaining("different receipt");
        verify(inventory, never()).insertReceipt(any(), any(), any());
    }

    @Test
    void missingWarehouseCannotCreateReceipt() {
        when(warehouses.find(organizationId, request.warehouseId(), true)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.receive(organizationId, actorId, request)).isInstanceOf(InventoryException.class).hasMessageContaining("not found");
        verifyNoInteractions(products);
        verify(inventory, never()).insertReceipt(any(), any(), any());
    }

    @Test
    void warehouseNamesAndOptionalLocationAreNormalized() {
        assertThat(new WarehouseValues("col-01", " Colombo Warehouse ", "  ", true).normalized())
                .isEqualTo(new WarehouseValues("COL-01", "Colombo Warehouse", null, true));
    }

    private StockMovement movement(long quantity) {
        return new StockMovement(UUID.randomUUID(), request.requestId(), request.warehouseId(), "WH-1", "Test warehouse",
                request.productId(), "SKU-1", "Test product", "Each", quantity, "DOC-1", null, actorId, "test-user", Instant.now());
    }
}
