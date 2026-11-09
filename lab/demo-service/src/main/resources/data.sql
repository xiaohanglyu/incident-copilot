-- Seed once. The table has to be large for the SLOW_QUERY release to hurt: a full scan of
-- a few thousand rows is still fast. 3M rows makes each scan take a few hundred ms.
INSERT INTO payments (customer_ref, merchant_ref, amount_cents, status, created_at)
SELECT 'CUS-' || lpad((g % 200000)::text, 6, '0'),
       'MER-' || lpad((g % 500)::text, 4, '0'),
       500 + (g::bigint * 7919) % 50000,
       'CAPTURED',
       now() - make_interval(secs => g)
FROM generate_series(1, 3000000) AS g
WHERE NOT EXISTS (SELECT 1 FROM payments);

ANALYZE payments;
