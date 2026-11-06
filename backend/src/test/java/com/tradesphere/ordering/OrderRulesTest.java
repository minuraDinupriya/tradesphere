package com.tradesphere.ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRulesTest {
    @Test
    void canonicalProductOrderAgreesWithPostgresUuidOrderWithoutMutatingInput() {
        var low = new OrderCreateRequest.Item(UUID.fromString("00000000-0000-4000-8000-000000000001"), 1L);
        var high = new OrderCreateRequest.Item(UUID.fromString("f0000000-0000-4000-8000-000000000001"), 2L);
        var inputs = new ArrayList<>(List.of(high, low));
        assertThat(OrderRules.canonicalItems(inputs)).containsExactly(low, high);
        assertThat(inputs).containsExactly(high, low);
    }

    @Test
    void rejectsMissingEmptyOversizedAndDuplicateItems() {
        var item = new OrderCreateRequest.Item(UUID.randomUUID(), 1L);
        assertThatThrownBy(() -> OrderRules.canonicalItems(null)).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> OrderRules.canonicalItems(List.of())).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> OrderRules.canonicalItems(java.util.Collections.nCopies(101, item))).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> OrderRules.canonicalItems(List.of(item, item))).isInstanceOf(OrderException.class).hasMessageContaining("once");
        var withNull = new ArrayList<OrderCreateRequest.Item>();
        withNull.add(null);
        assertThatThrownBy(() -> OrderRules.canonicalItems(withNull)).isInstanceOf(OrderException.class);
    }

    @Test
    void rejectsInvalidQuantitiesAndAllowsTheSupportedWholeUnitBoundary() {
        var id = UUID.randomUUID();
        for (Long quantity : new Long[]{null, 0L, -1L, 9007199254740992L}) {
            assertThatThrownBy(() -> OrderRules.canonicalItems(List.of(new OrderCreateRequest.Item(id, quantity)))).isInstanceOf(OrderException.class);
        }
        assertThatThrownBy(() -> OrderRules.canonicalItems(List.of(new OrderCreateRequest.Item(null, 1L)))).isInstanceOf(OrderException.class);
        assertThat(OrderRules.canonicalItems(List.of(new OrderCreateRequest.Item(id, 9007199254740991L))))
                .containsExactly(new OrderCreateRequest.Item(id, 9007199254740991L));
    }

    @Test
    void computesMoneyFromPriceSnapshotsExactlyWithoutBinaryFloatingPoint() {
        var items = List.of(new OrderItem(UUID.randomUUID(), "BOLT", "Test bolt", "Each", 2L, "12.50", "25.00"),
                new OrderItem(UUID.randomUUID(), "TAPE", "Test tape", "Each", 3L, "0.10", "0.30"));
        assertThat(OrderRules.total(items).toPlainString()).isEqualTo("25.30");
        assertThat(OrderRules.total(List.of()).toPlainString()).isEqualTo("0.00");
    }
}
