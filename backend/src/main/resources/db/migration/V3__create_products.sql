CREATE TABLE tradesphere.products (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES tradesphere.organizations(id),
    sku varchar(64) NOT NULL CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0),
    description varchar(2000),
    category varchar(80) NOT NULL CHECK (length(trim(category)) > 0),
    unit_of_measure varchar(32) NOT NULL CHECK (length(trim(unit_of_measure)) > 0),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_products_organization_sku ON tradesphere.products (organization_id, lower(sku));
CREATE INDEX idx_products_organization_name ON tradesphere.products (organization_id, lower(name), id);
