package com.tradesphere.ordering;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.tradesphere.catalog.CatalogProductAccess;
import com.tradesphere.inventory.InventoryOrderAccess;
import com.tradesphere.inventory.InsufficientStockException;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final CatalogProductAccess products;
    private final InventoryOrderAccess inventory;
    private final OrderHistoryRepository history;

    public OrderService(OrderRepository orders, CustomerRepository customers, CatalogProductAccess products, InventoryOrderAccess inventory, OrderHistoryRepository history) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.inventory = inventory;
        this.history = history;
    }

    @Transactional
    public Result create(UUID organizationId, UUID actorId, OrderCreateRequest request) {
        var inputs = OrderRules.canonicalItems(request.items());
        var existing = orders.byRequest(organizationId, request.requestId());
        if (existing.isPresent()) return replayCreation(organizationId, request, inputs, existing.get());
        var warehouse = inventory.lockActiveWarehouse(organizationId, request.warehouseId());
        var customer = activeCustomer(organizationId, request.customerId());
        var snapshots = snapshotProducts(organizationId, inputs);
        var inserted = orders.insert(organizationId, actorId, request, customer, warehouse, OrderRules.total(snapshots));
        if (inserted.isEmpty()) {
            return replayCreation(organizationId, request, inputs, orders.byRequest(organizationId, request.requestId()).orElseThrow());
        }
        orders.insertItems(organizationId, inserted.get().id(), snapshots);
        history.record(organizationId, inserted.get().id(), null, "PENDING", actorId);
        return new Result(detail(organizationId, inserted.get()), false);
    }

    @Transactional
    public Result confirm(UUID organizationId, UUID actorId, UUID id) {
        var order = orders.find(organizationId, id, true).orElseThrow(OrderService::notFound);
        if (history.recorded(organizationId, id, "CONFIRMED")) return new Result(detail(organizationId, order), true);
        OrderTransitions.next(order.status(), OrderTransitions.Action.CONFIRM);
        inventory.lockActiveWarehouse(organizationId, order.warehouseId());
        activeCustomer(organizationId, order.customerId());
        var items = orders.items(organizationId, id);
        var currentProducts = products.lockForOrder(organizationId, items.stream().map(OrderItem::productId).toList());
        if (currentProducts.stream().anyMatch(product -> !product.active())) throw conflict("Only active products can be confirmed.");
        try {
            inventory.reserve(organizationId, id, order.warehouseId(), actorId,
                    items.stream().map(item -> new InventoryOrderAccess.ReservationItem(item.productId(), item.quantity())).toList());
        } catch (InsufficientStockException failure) {
            var unavailable = items.stream().filter(item -> item.productId().equals(failure.productId())).findFirst().orElseThrow();
            throw conflict("Insufficient available stock for " + unavailable.sku() + " (" + unavailable.productName() + "). No items were reserved.");
        }
        orders.confirm(organizationId, id, actorId);
        history.record(organizationId, id, "PENDING", "CONFIRMED", actorId);
        return new Result(detail(organizationId, orders.find(organizationId, id, false).orElseThrow()), false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderDetail get(UUID organizationId, UUID id) {
        return detail(organizationId, orders.find(organizationId, id, false).orElseThrow(OrderService::notFound));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(UUID organizationId, String q, String status, int page, int size) {
        var items = orders.list(organizationId, q.strip(), status, page, size);
        var total = orders.count(organizationId, q.strip(), status);
        return new Page(items, total, page, size, (total + size - 1) / size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public History history(UUID organizationId, UUID id) {
        orders.find(organizationId, id, false).orElseThrow(OrderService::notFound);
        return new History(history.events(organizationId, id), inventory.movements(organizationId, id));
    }

    private Customer activeCustomer(UUID organizationId, UUID id) {
        var customer = customers.lockForOrder(organizationId, id).orElseThrow(() -> new CustomerException(HttpStatus.NOT_FOUND, "Customer not found in your organization."));
        if (!customer.active()) throw conflict("Orders require an active customer.");
        return customer;
    }

    private List<OrderItem> snapshotProducts(UUID organizationId, List<OrderCreateRequest.Item> inputs) {
        var locked = products.lockForOrder(organizationId, inputs.stream().map(OrderCreateRequest.Item::productId).toList());
        var byId = locked.stream().collect(java.util.stream.Collectors.toMap(CatalogProductAccess.OrderProduct::id, product -> product));
        return inputs.stream().map(input -> {
            var product = byId.get(input.productId());
            if (!product.active()) throw conflict("Orders require active products.");
            if (product.sellingPrice() == null) throw conflict("Set a selling price for every product before creating an order.");
            return new OrderItem(product.id(), product.sku(), product.name(), product.unitOfMeasure(), input.quantity(),
                    product.sellingPrice().toPlainString(), product.sellingPrice().multiply(BigDecimal.valueOf(input.quantity())).toPlainString());
        }).toList();
    }

    private Result replayCreation(UUID organizationId, OrderCreateRequest request, List<OrderCreateRequest.Item> inputs, OrderSummary original) {
        var saved = detail(organizationId, original);
        var savedInputs = saved.items().stream().map(item -> new OrderCreateRequest.Item(item.productId(), item.quantity())).toList();
        if (!original.customerId().equals(request.customerId()) || !original.warehouseId().equals(request.warehouseId()) || !savedInputs.equals(inputs)) {
            throw conflict("This request ID was already used for a different order. Start a new order.");
        }
        return new Result(saved, true);
    }

    private OrderDetail detail(UUID organizationId, OrderSummary order) { return OrderDetail.of(order, orders.items(organizationId, order.id())); }
    private static OrderException notFound() { return new OrderException(HttpStatus.NOT_FOUND, "Order not found in your organization."); }
    private static OrderException conflict(String message) { return new OrderException(HttpStatus.CONFLICT, message); }
    public record Result(OrderDetail order, boolean replayed) { }
    public record History(List<OrderStateEvent> transitions, List<com.tradesphere.inventory.OrderInventoryMovement> movements) { }
    public record Page(List<OrderSummary> items, long totalElements, int page, int size, long totalPages) { }
}
