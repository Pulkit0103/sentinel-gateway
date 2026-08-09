/**
 * Benchmark: Full production pipeline
 *
 * Purpose: end-to-end test of the complete request path:
 *   ThreatDetectionFilter → ApiKeyFilter → Spring Security (JWT) →
 *   JwtHeaders → TenantIsolation → RateLimit → Quotas → RouteAuth →
 *   LoadBalancer → upstream proxy → response
 *
 * Uses JWT auth (most production-like) with threat detection enabled.
 * This is the most representative benchmark for production performance.
 *
 * Run:
 *   export TOKEN=$(scripts/get-token.sh)
 *   k6 run -e GATEWAY_URL=http://localhost:8080 -e TOKEN=$TOKEN performance/k6/05-full-pipeline.js
 *
 * Baseline targets (with all filters active):
 *   P50 < 20ms, P95 < 100ms, P99 < 200ms
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { GATEWAY_URL, TOKEN, BASELINE_STAGES } from './shared/config.js';

const errorRate = new Rate('errors');
const pipelineLatency = new Trend('full_pipeline_latency', true);

export const options = {
  stages: BASELINE_STAGES,
  thresholds: {
    full_pipeline_latency: ['p(50)<20', 'p(95)<100', 'p(99)<200'],
    errors: ['rate<0.01'],
  },
};

export function setup() {
  if (!TOKEN) {
    throw new Error('TOKEN env var required. Run: export TOKEN=$(scripts/get-token.sh)');
  }
}

/** Rotate through all registered routes to exercise the full route table */
const ROUTES = [
  '/api/users',
  '/api/orders',
  '/api/payments',
];

export default function () {
  const route = ROUTES[Math.floor(Math.random() * ROUTES.length)];
  const params = {
    headers: { Authorization: `Bearer ${TOKEN}` },
  };

  const res = http.get(`${GATEWAY_URL}${route}`, params);

  const ok = check(res, {
    'not 401': (r) => r.status !== 401,
    'not 403': (r) => r.status !== 403,
    'not 429': (r) => r.status !== 429,
    'not 5xx': (r) => r.status < 500,
  });

  errorRate.add(!ok);
  pipelineLatency.add(res.timings.duration);

  sleep(0.01);
}
