package com.tradesphere.catalog;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository repository;
    private ProductService service;
    private final UUID organizationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final ProductValues values = new ProductValues("bolt-01", "  Steel bolt  ", "  ", " Hardware ", " Each ", true);

    @BeforeEach
    void setUp() { service = new ProductService(repository); }

    @Test
    void normalizesBusinessValuesBeforePersistence() {
        service.create(organizationId, values);
        var captured = ArgumentCaptor.forClass(ProductValues.class);
        verify(repository).create(eq(organizationId), captured.capture());
        assertThat(captured.getValue()).isEqualTo(new ProductValues("BOLT-01", "Steel bolt", null, "Hardware", "Each", true));
    }

    @Test
    void translatesDatabaseUniquenessFailureToUsefulConflict() {
        when(repository.create(eq(organizationId), any())).thenThrow(new DuplicateKeyException("internal SQL"));
        assertThatThrownBy(() -> service.create(organizationId, values)).isInstanceOf(ProductConflictException.class)
                .hasMessage("That SKU already exists in your organization.");
    }

    @Test
    void absentAndOtherOrganizationProductHaveTheSameNotFoundBehavior() {
        when(repository.find(organizationId, productId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(organizationId, productId)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void missingUpdateDoesNotRevealAnotherOrganizationRecord() {
        when(repository.update(eq(organizationId), eq(productId), any(), eq(0L))).thenReturn(Optional.empty());
        when(repository.find(organizationId, productId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(organizationId, productId, values, 0)).isInstanceOf(ProductNotFoundException.class);
    }
}
