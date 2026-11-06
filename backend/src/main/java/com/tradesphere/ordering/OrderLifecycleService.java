package com.tradesphere.ordering;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tradesphere.inventory.InventoryOrderAccess;
import static com.tradesphere.ordering.OrderTransitions.Action;

@Service
public class OrderLifecycleService {
    private final OrderRepository orders;
    private final OrderHistoryRepository history;
    private final InventoryOrderAccess inventory;
    public OrderLifecycleService(OrderRepository orders, OrderHistoryRepository history, InventoryOrderAccess inventory) {
        this.orders = orders; this.history = history; this.inventory = inventory;
    }

    @Transactional
    public OrderService.Result act(UUID organizationId, UUID actorId, UUID id, Action action) {
        if (action == Action.CONFIRM) throw new IllegalArgumentException("Confirmation belongs to OrderService.");
        var order = orders.find(organizationId, id, true).orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "Order not found in your organization."));
        // Recorded actions replay their result without regressing a later/terminal state or repeating inventory effects.
        if (history.recorded(organizationId, id, action.target())) return result(organizationId, order, true);
        var target = OrderTransitions.next(order.status(), action);
        var items = orders.items(organizationId, id);
        if (action == Action.FULFILL || (action == Action.CANCEL && order.status().equals("CONFIRMED"))) {
            inventory.closeReservations(organizationId, id, order.warehouseId(), actorId,
                    items.stream().map(item -> new InventoryOrderAccess.ReservationItem(item.productId(), item.quantity())).toList(), action == Action.FULFILL);
        }
        orders.transition(organizationId, id, order.status(), target);
        history.record(organizationId, id, order.status(), target, actorId);
        return result(organizationId, orders.find(organizationId, id, false).orElseThrow(), false);
    }

    private OrderService.Result result(UUID organizationId, OrderSummary order, boolean replayed) {
        return new OrderService.Result(OrderDetail.of(order, orders.items(organizationId, order.id())), replayed);
    }
}
