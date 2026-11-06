package com.tradesphere.ordering;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    public CustomerService(CustomerRepository customers) { this.customers = customers; }

    @Transactional
    public Customer create(UUID organizationId, CustomerValues values) { return customers.create(organizationId, values.normalized()); }

    @Transactional(readOnly = true)
    public Customer get(UUID organizationId, UUID id) {
        return customers.find(organizationId, id).orElseThrow(() -> new CustomerException(HttpStatus.NOT_FOUND, "Customer not found in your organization."));
    }

    @Transactional
    public Customer update(UUID organizationId, UUID id, CustomerValues values, long version) {
        return customers.update(organizationId, id, values.normalized(), version).orElseGet(() -> {
            get(organizationId, id);
            throw new CustomerException(HttpStatus.CONFLICT, "This customer changed. Reload the latest customer before saving again.");
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerPage list(UUID organizationId, String q, Boolean active, int page, int size) {
        var items = customers.list(organizationId, q.strip(), active, page, size);
        var total = customers.count(organizationId, q.strip(), active);
        return new CustomerPage(items, total, page, size, (total + size - 1) / size);
    }

    public record CustomerPage(List<Customer> items, long totalElements, int page, int size, long totalPages) { }
}
