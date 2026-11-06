ALTER TABLE tradesphere.users ADD COLUMN can_write_inventory boolean NOT NULL DEFAULT false;
ALTER TABLE tradesphere.users ADD CONSTRAINT uq_users_organization_id_id UNIQUE (organization_id, id);
ALTER TABLE tradesphere.products ADD CONSTRAINT uq_products_organization_id_id UNIQUE (organization_id, id);

CREATE TABLE tradesphere.warehouses (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES tradesphere.organizations(id),
    code varchar(32) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0),
    location varchar(240),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_warehouses_organization_id_id UNIQUE (organization_id, id)
);
CREATE UNIQUE INDEX uq_warehouses_organization_code ON tradesphere.warehouses (organization_id, lower(code));
CREATE INDEX idx_warehouses_organization_name ON tradesphere.warehouses (organization_id, lower(name), id);

-- Quantities use positive whole units and stay inside JavaScript's exact integer range.
CREATE TABLE tradesphere.inventory_balances (
    organization_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    product_id uuid NOT NULL,
    on_hand bigint NOT NULL DEFAULT 0 CHECK (on_hand BETWEEN 0 AND 9007199254740991),
    reserved bigint NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= on_hand),
    available bigint GENERATED ALWAYS AS (on_hand - reserved) STORED,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, warehouse_id, product_id),
    CONSTRAINT fk_balances_organization_warehouse FOREIGN KEY (organization_id, warehouse_id)
        REFERENCES tradesphere.warehouses(organization_id, id),
    CONSTRAINT fk_balances_organization_product FOREIGN KEY (organization_id, product_id)
        REFERENCES tradesphere.products(organization_id, id)
);

CREATE TABLE tradesphere.stock_movements (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    request_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity bigint NOT NULL CHECK (quantity BETWEEN 1 AND 9007199254740991),
    reference varchar(120),
    note varchar(500),
    received_by uuid NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_movements_organization_request UNIQUE (organization_id, request_id),
    CONSTRAINT fk_movements_organization_warehouse FOREIGN KEY (organization_id, warehouse_id)
        REFERENCES tradesphere.warehouses(organization_id, id),
    CONSTRAINT fk_movements_organization_product FOREIGN KEY (organization_id, product_id)
        REFERENCES tradesphere.products(organization_id, id),
    CONSTRAINT fk_movements_organization_user FOREIGN KEY (organization_id, received_by)
        REFERENCES tradesphere.users(organization_id, id)
);
CREATE INDEX idx_movements_organization_received ON tradesphere.stock_movements (organization_id, received_at DESC, id DESC);
