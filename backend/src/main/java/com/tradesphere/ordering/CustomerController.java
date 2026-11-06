package com.tradesphere.ordering;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tradesphere.identity.OrganizationUser;

@RestController
@Validated
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers) { this.customers = customers; }

    @PostMapping
    public ResponseEntity<Customer> create(@AuthenticationPrincipal OrganizationUser user, @Valid @RequestBody CustomerValues values) {
        var customer = customers.create(user.organizationId(), values);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
    }

    @GetMapping("/{id}")
    public Customer get(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) { return customers.get(user.organizationId(), id); }

    @PutMapping("/{id}")
    public Customer update(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest request) {
        return customers.update(user.organizationId(), id, request.values(), request.version());
    }

    @GetMapping
    public CustomerService.CustomerPage list(@AuthenticationPrincipal OrganizationUser user,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customers.list(user.organizationId(), q, active, page, size);
    }
}
