/**
 * Benchmark: Full pipeline with JWT authentication
 *
 * Purpose: measures gateway overhead when JWT validation is in the path.
 * The JWKS public key is cached by Spring Security after first fetch, so
 * this tests RS256 signature verification + Spring Security filter chain
 * on every request.
 *
 * Requires a pre-obtained JWT:
 *   export TOKEN=$(curl -s -X POST http://localhost:8180/realms/sentinel/... | jq -r .access_token)
 *   k6 run -e GATEWAY_URL=http://localhost:8080 -e TOKEN=$TOKEN performance/k6/02-jwt-auth.js
 *
 * Baseline targets:
 *   P50 < 10ms, P95 < 50ms, P99 < 100ms
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { GATEWAY_URL, TOKEN, BASELINE_STAGES } from './shared/config.js';

const errorRate = new Rate('errors');
const authLatency = new Trend('jwt_auth_latency', true);

export const options = {
  stages: BASELINE_STAGES,
  thresholds: {
    jwt_auth_latency: ['p(50)<10', 'p(95)<50', 'p(99)<100'],
    errors: ['rate<0.01'],
  },
};

export function setup() {
  if (!TOKEN) {
    throw new Error('TOKEN env var required. Run: export TOKEN=$(scripts/get-token.sh)');
  }
}

export default function () {
  const params = {
    headers: { Authorization: `Bearer ${TOKEN}` },
  };

  const res = http.get(`${GATEWAY_URL}/api/users`, params);

  const ok = check(res, {
    'not 401': (r) => r.status !== 401,
    'not 403': (r) => r.status !== 403,
    'not 500': (r) => r.status < 500,
  });

  errorRate.add(!ok);
  authLatency.add(res.timings.duration);

  sleep(0.01);
}
