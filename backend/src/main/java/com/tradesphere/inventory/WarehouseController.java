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
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {
    private final WarehouseService warehouses;
    public WarehouseController(WarehouseService warehouses) { this.warehouses = warehouses; }

    @PostMapping
    public ResponseEntity<Warehouse> create(@AuthenticationPrincipal OrganizationUser user, @Valid @RequestBody WarehouseValues values) {
        var warehouse = warehouses.create(user.organizationId(), values);
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + warehouse.id())).body(warehouse);
    }

    @GetMapping("/{id}")
    public Warehouse get(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) { return warehouses.get(user.organizationId(), id); }

    @PutMapping("/{id}")
    public Warehouse update(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return warehouses.update(user.organizationId(), id, request.values(), request.version());
    }

    @GetMapping
    public InventoryPage<Warehouse> list(@AuthenticationPrincipal OrganizationUser user,
                                        @RequestParam(defaultValue = "") @Size(max = 100) String q,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return warehouses.list(user.organizationId(), q, active, page, size);
    }

}
