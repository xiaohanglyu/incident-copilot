// Steady traffic for payment-service. Rates are the baseline the scenarios degrade from;
// change them here and every scenario's numbers change with them.
import http from 'k6/http';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SEEDED = 3000000;     // rows from data.sql; refunds target these ids
const CUSTOMERS = 200000;   // distinct customer_ref values in the seed
const JSON_HEADERS = { 'Content-Type': 'application/json' };

function steady(exec, rate, maxVUs) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    duration: '24h',
    preAllocatedVUs: 20,
    maxVUs,
  };
}

export const options = {
  scenarios: {
    create: steady('create', 20, 300),
    history: steady('history', 30, 400),
    refund: steady('refund', 4, 200),
  },
  // Scenarios end with `docker compose stop load`; no summary is wanted.
  summaryTrendStats: [],
};

const customer = () => 'CUS-' + String(Math.floor(Math.random() * CUSTOMERS)).padStart(6, '0');

export function create() {
  const body = JSON.stringify({
    customerRef: customer(),
    merchantRef: 'MER-' + String(Math.floor(Math.random() * 500)).padStart(4, '0'),
    amountCents: 500 + Math.floor(Math.random() * 50000),
  });
  const key = `${__VU}-${__ITER}-${Date.now()}`;
  http.post(`${BASE}/payments`, body,
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': key }, tags: { name: 'create' } });
}

export function history() {
  http.get(`${BASE}/payments?customerRef=${customer()}`, { tags: { name: 'history' } });
}

export function refund() {
  const id = 1 + Math.floor(Math.random() * SEEDED);
  // About 3% ask for more than was paid. Clients do this; the service rejects it with 422
  // and logs a WARN, so those warnings are part of the healthy baseline.
  const amountCents = Math.random() < 0.03 ? 10000000 : 100;
  http.post(`${BASE}/payments/${id}/refunds`, JSON.stringify({ amountCents }),
    { headers: JSON_HEADERS, tags: { name: 'refund' } });
}
