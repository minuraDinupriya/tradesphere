package com.tradesphere.inventory;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tradesphere.identity.OrganizationUser;

@RestController
@Validated
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @PostMapping("/receipts")
    public ResponseEntity<InventoryService.ReceiptResult> receive(@AuthenticationPrincipal OrganizationUser user, @Valid @RequestBody StockReceiptRequest request) {
        var result = inventory.receive(user.organizationId(), user.id(), request);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/api/v1/inventory/movements/" + result.movement().id())).body(result);
    }

    @GetMapping
    public InventoryPage<InventoryBalance> balances(@AuthenticationPrincipal OrganizationUser user,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventory.balances(user.organizationId(), q, warehouseId, productId, page, size);
    }

    @GetMapping("/movements")
    public InventoryPage<StockMovement> movements(@AuthenticationPrincipal OrganizationUser user,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventory.movements(user.organizationId(), q, warehouseId, productId, page, size);
    }

    @GetMapping("/movements/{id}")
    public StockMovement movement(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) { return inventory.movement(user.organizationId(), id); }
}
