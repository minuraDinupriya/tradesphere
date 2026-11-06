ALTER TABLE tradesphere.orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE tradesphere.orders DROP CONSTRAINT ck_orders_confirmation;
ALTER TABLE tradesphere.orders
    ADD CONSTRAINT ck_orders_status CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','FULFILLED','CANCELLED')),
    ADD CONSTRAINT ck_orders_confirmation CHECK (
        (status IN ('PENDING','CANCELLED') AND confirmed_at IS NULL AND confirmed_by IS NULL) OR
        (status IN ('CONFIRMED','PROCESSING','FULFILLED','CANCELLED') AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL));

ALTER TABLE tradesphere.stock_reservations
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN closed_by uuid,
    ADD COLUMN closed_at timestamptz,
    ADD CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE','RELEASED','CONSUMED')),
    ADD CONSTRAINT ck_reservations_closure CHECK (
        (status = 'ACTIVE' AND closed_by IS NULL AND closed_at IS NULL) OR
        (status IN ('RELEASED','CONSUMED') AND closed_by IS NOT NULL AND closed_at IS NOT NULL)),
    ADD CONSTRAINT fk_reservations_closer FOREIGN KEY (organization_id, closed_by) REFERENCES tradesphere.users(organization_id,id);

CREATE TABLE tradesphere.order_state_history (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    order_id uuid NOT NULL,
    from_status varchar(16),
    to_status varchar(16) NOT NULL,
    actor_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    backfilled boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_order_history_target UNIQUE (organization_id,order_id,to_status),
    CONSTRAINT fk_order_history_order FOREIGN KEY (organization_id,order_id) REFERENCES tradesphere.orders(organization_id,id),
    CONSTRAINT fk_order_history_actor FOREIGN KEY (organization_id,actor_id) REFERENCES tradesphere.users(organization_id,id),
    CONSTRAINT ck_order_history_transition CHECK (
        (from_status IS NULL AND to_status = 'PENDING') OR
        (from_status IS NOT NULL AND (
            (from_status = 'PENDING' AND to_status IN ('CONFIRMED','CANCELLED')) OR
            (from_status = 'CONFIRMED' AND to_status IN ('PROCESSING','CANCELLED')) OR
            (from_status = 'PROCESSING' AND to_status = 'FULFILLED'))))
);
-- Reconstruct only facts represented by existing metadata, explicitly marked as backfilled.
INSERT INTO tradesphere.order_state_history (id,organization_id,order_id,from_status,to_status,actor_id,occurred_at,backfilled)
SELECT gen_random_uuid(),organization_id,id,NULL,'PENDING',created_by,created_at,true FROM tradesphere.orders;
INSERT INTO tradesphere.order_state_history (id,organization_id,order_id,from_status,to_status,actor_id,occurred_at,backfilled)
SELECT gen_random_uuid(),organization_id,id,'PENDING','CONFIRMED',confirmed_by,confirmed_at,true
FROM tradesphere.orders WHERE confirmed_at IS NOT NULL;

CREATE TABLE tradesphere.order_inventory_movements (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    order_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    product_id uuid NOT NULL,
    kind varchar(16) NOT NULL,
    quantity bigint NOT NULL CHECK (quantity BETWEEN 1 AND 9007199254740991),
    on_hand_delta bigint NOT NULL,
    reserved_delta bigint NOT NULL,
    actor_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_movement_reservation UNIQUE (organization_id,order_id,product_id),
    CONSTRAINT ck_order_movement_deltas CHECK (
        (kind = 'RELEASE' AND on_hand_delta = 0 AND reserved_delta = -quantity) OR
        (kind = 'FULFILL' AND on_hand_delta = -quantity AND reserved_delta = -quantity)),
    CONSTRAINT fk_order_movement_reservation FOREIGN KEY (organization_id,order_id,product_id)
        REFERENCES tradesphere.stock_reservations(organization_id,order_id,product_id),
    CONSTRAINT fk_order_movement_warehouse FOREIGN KEY (organization_id,order_id,warehouse_id)
        REFERENCES tradesphere.orders(organization_id,id,warehouse_id),
    CONSTRAINT fk_order_movement_actor FOREIGN KEY (organization_id,actor_id) REFERENCES tradesphere.users(organization_id,id)
);
CREATE INDEX idx_order_history_time ON tradesphere.order_state_history (organization_id,order_id,occurred_at,id);
