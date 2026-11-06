package com.tradesphere.catalog;

import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal OrganizationUser user,
                                                  @Valid @RequestBody CreateProductRequest request) {
        var product = products.create(user.organizationId(), request.values());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(ProductResponse.from(product));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id) {
        return ProductResponse.from(products.get(user.organizationId(), id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@AuthenticationPrincipal OrganizationUser user, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(products.update(user.organizationId(), id, request.values(), request.version()));
    }

    @GetMapping
    public ProductPageResponse list(@AuthenticationPrincipal OrganizationUser user,
                                    @RequestParam(defaultValue = "") @Size(max = 100) String q,
                                    @RequestParam(defaultValue = "") @Size(max = 80) String category,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = products.list(user.organizationId(), q, category, active, page, size);
        return new ProductPageResponse(result.items().stream().map(ProductResponse::from).toList(),
                result.totalElements(), result.page(), result.size(), result.totalPages());
    }

    @GetMapping("/categories")
    public List<String> categories(@AuthenticationPrincipal OrganizationUser user) { return products.categories(user.organizationId()); }

    public record ProductPageResponse(List<ProductResponse> items, long totalElements, int page, int size, int totalPages) { }
}
