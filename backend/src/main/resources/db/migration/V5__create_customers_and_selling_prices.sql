ALTER TABLE tradesphere.users
    ADD COLUMN can_write_customers boolean NOT NULL DEFAULT false,
    ADD COLUMN can_write_prices boolean NOT NULL DEFAULT false;

ALTER TABLE tradesphere.products
    ADD COLUMN selling_price numeric(14, 2),
    ADD COLUMN price_version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_products_selling_price CHECK (selling_price >= 0 AND selling_price <> 'NaN'::numeric),
    ADD CONSTRAINT ck_products_price_version CHECK (price_version >= 0);

CREATE TABLE tradesphere.customers (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES tradesphere.organizations(id),
    name varchar(160) NOT NULL,
    contact_name varchar(160),
    email varchar(254),
    phone varchar(40),
    address varchar(1000),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_customers_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_customers_version CHECK (version >= 0),
    CONSTRAINT uq_customers_organization_id_id UNIQUE (organization_id, id)
);
CREATE INDEX idx_customers_organization_name ON tradesphere.customers (organization_id, lower(name), id);
