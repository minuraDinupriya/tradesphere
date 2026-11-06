ALTER TABLE tradesphere.users ADD COLUMN can_write_orders boolean NOT NULL DEFAULT false;

CREATE TABLE tradesphere.orders (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    request_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    customer_name varchar(160) NOT NULL,
    warehouse_id uuid NOT NULL,
    warehouse_name varchar(160) NOT NULL,
    warehouse_code varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    currency varchar(3) NOT NULL DEFAULT 'LKR',
    total_amount numeric(32,2) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_by uuid,
    confirmed_at timestamptz,
    CONSTRAINT uq_orders_organization_request UNIQUE (organization_id, request_id),
    CONSTRAINT uq_orders_organization_id UNIQUE (organization_id, id),
    CONSTRAINT uq_orders_organization_id_warehouse UNIQUE (organization_id, id, warehouse_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_orders_confirmation CHECK (
        (status = 'PENDING' AND confirmed_at IS NULL AND confirmed_by IS NULL) OR
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)),
    CONSTRAINT ck_orders_currency CHECK (currency = 'LKR'),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0 AND total_amount <> 'NaN'::numeric),
    CONSTRAINT fk_orders_customer FOREIGN KEY (organization_id, customer_id) REFERENCES tradesphere.customers(organization_id, id),
    CONSTRAINT fk_orders_warehouse FOREIGN KEY (organization_id, warehouse_id) REFERENCES tradesphere.warehouses(organization_id, id),
    CONSTRAINT fk_orders_creator FOREIGN KEY (organization_id, created_by) REFERENCES tradesphere.users(organization_id, id),
    CONSTRAINT fk_orders_confirmer FOREIGN KEY (organization_id, confirmed_by) REFERENCES tradesphere.users(organization_id, id)
);
CREATE INDEX idx_orders_organization_created ON tradesphere.orders (organization_id, created_at DESC, id DESC);

CREATE TABLE tradesphere.order_items (
    organization_id uuid NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    sku varchar(64) NOT NULL,
    product_name varchar(160) NOT NULL,
    unit_of_measure varchar(32) NOT NULL,
    quantity bigint NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    line_total numeric(32,2) GENERATED ALWAYS AS (unit_price * quantity) STORED,
    PRIMARY KEY (organization_id, order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 9007199254740991),
    CONSTRAINT ck_order_items_price CHECK (unit_price >= 0 AND unit_price <> 'NaN'::numeric),
    CONSTRAINT fk_order_items_order FOREIGN KEY (organization_id, order_id) REFERENCES tradesphere.orders(organization_id, id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (organization_id, product_id) REFERENCES tradesphere.products(organization_id, id)
);

-- Reservations allocate stock; stock_movements continues to represent physical receipts only.
CREATE TABLE tradesphere.stock_reservations (
    organization_id uuid NOT NULL,
    order_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity bigint NOT NULL,
    reserved_by uuid NOT NULL,
    reserved_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, order_id, product_id),
    CONSTRAINT ck_reservations_quantity CHECK (quantity BETWEEN 1 AND 9007199254740991),
    CONSTRAINT fk_reservations_order_warehouse FOREIGN KEY (organization_id, order_id, warehouse_id)
        REFERENCES tradesphere.orders(organization_id, id, warehouse_id),
    CONSTRAINT fk_reservations_order_item FOREIGN KEY (organization_id, order_id, product_id)
        REFERENCES tradesphere.order_items(organization_id, order_id, product_id),
    CONSTRAINT fk_reservations_balance FOREIGN KEY (organization_id, warehouse_id, product_id)
        REFERENCES tradesphere.inventory_balances(organization_id, warehouse_id, product_id),
    CONSTRAINT fk_reservations_actor FOREIGN KEY (organization_id, reserved_by) REFERENCES tradesphere.users(organization_id, id)
);
