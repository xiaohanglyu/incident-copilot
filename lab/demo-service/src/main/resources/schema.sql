CREATE TABLE IF NOT EXISTS payments (
    id             BIGSERIAL PRIMARY KEY,
    customer_ref   TEXT        NOT NULL,
    merchant_ref   TEXT        NOT NULL,
    amount_cents   BIGINT      NOT NULL,
    refunded_cents BIGINT      NOT NULL DEFAULT 0,
    status         TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS payments_customer_created
    ON payments (customer_ref, created_at DESC);
