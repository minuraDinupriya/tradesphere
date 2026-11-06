CREATE TABLE tradesphere.organizations (
    id uuid PRIMARY KEY,
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0)
);

-- TRD-13 deliberately supports one organization per user, not organization switching.
CREATE TABLE tradesphere.users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES tradesphere.organizations(id),
    username varchar(80) NOT NULL,
    password_hash varchar(100) NOT NULL,
    can_write_catalog boolean NOT NULL DEFAULT false,
    enabled boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX uq_users_username ON tradesphere.users (lower(username));
CREATE INDEX idx_users_organization_id ON tradesphere.users (organization_id);
