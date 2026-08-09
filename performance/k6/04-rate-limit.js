/**
 * Benchmark: Rate limiting overhead
 *
 * Purpose: measures the cost of Redis-backed rate limiting. Each request
 * performs an INCR+EXPIRE Lua script in Redis. This script tests:
 *   1. Normal throughput — rate limiter allows and adds a small overhead
 *   2. Rate limit hit — 429 responses at high concurrency
 *
 * The scenario ramps to a high VU count to deliberately trigger 429s
 * from the ANONYMOUS policy (100 req/min per IP). The check accepts
 * 429 as a valid response (rate limit working correctly).
 *
 * Run:
 *   k6 run -e GATEWAY_URL=http://localhost:8080 performance/k6/04-rate-limit.js
 *
 * Notes:
 *   - Rate limiting must be enabled: SENTINEL_RATE_LIMIT_ENABLED=true
 *   - Redis must be running: REDIS_HOST=redis REDIS_PORT=6379
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { GATEWAY_URL } from './shared/config.js';

const errorRate = new Rate('errors');
const rateLimitedCount = new Counter('rate_limited');
const rateLimitLatency = new Trend('rate_limit_latency', true);

export const options = {
  stages: [
    { duration: '30s', target: 100  },  // ramp to 1K req/s
    { duration: '1m',  target: 100  },  // hold — expect some 429s at ANONYMOUS policy
    { duration: '30s', target: 500  },  // ramp to 5K req/s — lots of 429s
    { duration: '1m',  target: 500  },  // hold 5K
    { duration: '30s', target: 0    },  // cool down
  ],
  thresholds: {
    rate_limit_latency: ['p(95)<100'],  // Redis call adds latency even for allowed requests
    errors: ['rate<0.05'],              // Accept up to 5% error (not counting 429s)
  },
};

export default function () {
  const res = http.get(`${GATEWAY_URL}/actuator/health`);

  if (res.status === 429) {
    rateLimitedCount.add(1);
  }

  const ok = check(res, {
    'status 200 or 429': (r) => r.status === 200 || r.status === 429,
    'not 500': (r) => r.status < 500,
  });

  errorRate.add(!ok);
  rateLimitLatency.add(res.timings.duration);

  sleep(0.01);
}
