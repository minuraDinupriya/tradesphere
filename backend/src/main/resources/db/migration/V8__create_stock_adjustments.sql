CREATE TABLE tradesphere.stock_adjustments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    request_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    product_id uuid NOT NULL,
    previous_on_hand bigint NOT NULL CHECK (previous_on_hand BETWEEN 0 AND 9007199254740991),
    counted_on_hand bigint NOT NULL CHECK (counted_on_hand BETWEEN 0 AND 9007199254740991),
    reserved bigint NOT NULL CHECK (reserved BETWEEN 0 AND least(previous_on_hand, counted_on_hand)),
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    actor_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_stock_adjustments_changed CHECK (previous_on_hand <> counted_on_hand),
    CONSTRAINT uq_stock_adjustments_organization_request UNIQUE (organization_id, request_id),
    CONSTRAINT fk_stock_adjustments_balance FOREIGN KEY (organization_id, warehouse_id, product_id)
        REFERENCES tradesphere.inventory_balances (organization_id, warehouse_id, product_id),
    CONSTRAINT fk_stock_adjustments_actor FOREIGN KEY (organization_id, actor_id)
        REFERENCES tradesphere.users (organization_id, id)
);

CREATE INDEX idx_stock_adjustments_balance_history ON tradesphere.stock_adjustments
    (organization_id, warehouse_id, product_id, occurred_at DESC, id DESC);
