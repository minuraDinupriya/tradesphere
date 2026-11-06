package com.tradesphere.ordering;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tradesphere.identity.OrganizationUser;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping
    public ResponseEntity<OrderService.Result> create(@AuthenticationPrincipal OrganizationUser user, @Valid @RequestBody OrderCreateRequest request) {
        var result = orders.create(user.organizationId(), user.id(), request);
        return ResponseEntity.status(result.replayed() ? 200 : 201).location(URI.create("/api/v1/orders/" + result.order().id())).body(result);
    }

    @PostMapping("/{id}/confirm")
    public OrderService.Result confirm(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) {
        return orders.confirm(user.organizationId(), user.id(), id);
    }

    @GetMapping("/{id}")
    public OrderDetail get(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) { return orders.get(user.organizationId(), id); }

    @GetMapping
    public OrderService.Page list(@AuthenticationPrincipal OrganizationUser user,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|PENDING|CONFIRMED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.list(user.organizationId(), q, status, page, size);
    }
}
