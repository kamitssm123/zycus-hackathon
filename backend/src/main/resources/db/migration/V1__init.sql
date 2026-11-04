-- Flyway owns the schema. Never edit an applied migration; add V2__, V3__ ... instead.
CREATE TABLE items (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    description text,
    done        boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_name ON items (lower(name));
