package com.tradesphere.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) { this.products = products; }

    @Transactional
    public Product create(UUID organizationId, ProductValues values) {
        try {
            return products.create(organizationId, values.normalized());
        } catch (DuplicateKeyException exception) {
            throw new ProductConflictException("That SKU already exists in your organization.");
        }
    }

    @Transactional(readOnly = true)
    public Product get(UUID organizationId, UUID id) {
        return products.find(organizationId, id).orElseThrow(ProductNotFoundException::new);
    }

    @Transactional
    public Product update(UUID organizationId, UUID id, ProductValues values, long version) {
        try {
            return products.update(organizationId, id, values.normalized(), version).orElseGet(() -> {
                get(organizationId, id); // Return the same 404 for absent and other-organization identifiers.
                throw new ProductConflictException("This product changed since you opened it. Reload the latest product before saving again.");
            });
        } catch (DuplicateKeyException exception) {
            throw new ProductConflictException("That SKU already exists in your organization.");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductPage list(UUID organizationId, String query, String category, Boolean active, int page, int size) {
        var items = products.list(organizationId, query.strip(), category.strip(), active, page, size);
        var total = products.count(organizationId, query.strip(), category.strip(), active);
        return new ProductPage(items, total, page, size, (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public List<String> categories(UUID organizationId) { return products.categories(organizationId); }

    public record ProductPage(List<Product> items, long totalElements, int page, int size, int totalPages) { }
}
