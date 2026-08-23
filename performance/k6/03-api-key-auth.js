/**
 * Benchmark: API key authentication
 *
 * Purpose: measures the overhead of API key authentication path:
 * X-API-Key header → SHA-256 hash → R2DBC PostgreSQL lookup → status check.
 * Contrasts with JWT auth to show the relative cost of synchronous DB lookups.
 *
 * Requires a pre-created, active API key:
 *   export API_KEY=sgk_...
 *   k6 run -e GATEWAY_URL=http://localhost:8080 -e API_KEY=$API_KEY performance/k6/03-api-key-auth.js
 *
 * Baseline targets:
 *   P50 < 15ms, P95 < 60ms, P99 < 150ms
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { GATEWAY_URL, API_KEY, BASELINE_STAGES } from './shared/config.js';

const errorRate = new Rate('errors');
const apiKeyLatency = new Trend('api_key_latency', true);

export const options = {
  stages: BASELINE_STAGES,
  thresholds: {
    api_key_latency: ['p(50)<15', 'p(95)<60', 'p(99)<150'],
    errors: ['rate<0.01'],
  },
};

export function setup() {
  if (!API_KEY) {
    throw new Error('API_KEY env var required.');
  }
}

export default function () {
  const params = {
    headers: { 'X-API-Key': API_KEY },
  };

  const res = http.get(`${GATEWAY_URL}/api/users`, params);

  const ok = check(res, {
    'not 401': (r) => r.status !== 401,
    'not 403': (r) => r.status !== 403,
    'not 500': (r) => r.status < 500,
  });

  errorRate.add(!ok);
  apiKeyLatency.add(res.timings.duration);

  sleep(0.01);
}
