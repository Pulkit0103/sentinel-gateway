/**
 * Benchmark: Routing only (actuator/health — no auth, no filters)
 *
 * Purpose: measures pure gateway overhead — Netty → route → upstream → response.
 * Uses /actuator/health which is public and bypasses all security filters.
 * This is the theoretical minimum latency floor.
 *
 * Baseline targets:
 *   P50 < 5ms, P95 < 20ms, P99 < 50ms
 *
 * Run:
 *   k6 run -e GATEWAY_URL=http://localhost:8080 performance/k6/01-routing-only.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { GATEWAY_URL, BASELINE_STAGES } from './shared/config.js';

const errorRate = new Rate('errors');
const routingLatency = new Trend('routing_latency', true);

export const options = {
  stages: BASELINE_STAGES,
  thresholds: {
    routing_latency: ['p(50)<5', 'p(95)<20', 'p(99)<50'],
    errors: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(`${GATEWAY_URL}/actuator/health`);

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'health UP': (r) => r.body.includes('"status":"UP"'),
  });

  errorRate.add(!ok);
  routingLatency.add(res.timings.duration);

  sleep(0.01);
}
