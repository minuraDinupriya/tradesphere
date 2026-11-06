package com.tradesphere.ordering;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTransitionsTest {
    @Test
    void acceptsExactlyTheApprovedFiveTransitionsAndRejectsEveryOtherPair() {
        for (var state : new String[]{"PENDING","CONFIRMED","PROCESSING","FULFILLED","CANCELLED"}) {
            for (var action : OrderTransitions.Action.values()) {
                var permitted = (state.equals("PENDING") && (action == OrderTransitions.Action.CONFIRM || action == OrderTransitions.Action.CANCEL))
                        || (state.equals("CONFIRMED") && (action == OrderTransitions.Action.PROCESS || action == OrderTransitions.Action.CANCEL))
                        || (state.equals("PROCESSING") && action == OrderTransitions.Action.FULFILL);
                if (permitted) assertThat(OrderTransitions.next(state, action)).isEqualTo(action.target());
                else assertThatThrownBy(() -> OrderTransitions.next(state, action)).as(state + " / " + action)
                        .isInstanceOf(OrderException.class).hasMessageContaining("Reload the latest order");
            }
        }
    }
}
