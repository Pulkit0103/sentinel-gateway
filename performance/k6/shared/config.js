/**
 * Shared configuration for Sentinel Gateway k6 benchmarks.
 * Override via environment variables: k6 run -e GATEWAY_URL=http://... script.js
 */
export const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

/** Pre-obtain a JWT by running: scripts/get-token.sh and exporting TOKEN */
export const TOKEN = __ENV.TOKEN || '';

/** Pre-created API key for API key auth scenarios */
export const API_KEY = __ENV.API_KEY || '';

/**
 * Standard load stages used across all scenarios.
 *
 * Ramp up → 1K req/s → ramp up → 5K req/s → ramp up → 10K req/s → cool down.
 * Each plateau is held for 2 minutes to capture steady-state metrics.
 *
 * Note: 10K req/s on a single gateway instance requires adequate CPU/memory.
 * Run with --vus 500 or higher if the ramp doesn't reach target throughput.
 */
export const BASELINE_STAGES = [
  { duration: '30s',  target: 50   },  // ramp to 1K req/s equivalent (50 VUs)
  { duration: '2m',   target: 50   },  // hold 1K
  { duration: '30s',  target: 250  },  // ramp to 5K req/s
  { duration: '2m',   target: 250  },  // hold 5K
  { duration: '30s',  target: 500  },  // ramp to 10K req/s
  { duration: '2m',   target: 500  },  // hold 10K
  { duration: '30s',  target: 0    },  // cool down
];

/** Thresholds that must pass for the benchmark to be considered successful */
export const THRESHOLDS = {
  http_req_duration: [
    'p(50)<50',    // P50 under 50ms
    'p(95)<200',   // P95 under 200ms
    'p(99)<500',   // P99 under 500ms
  ],
  http_req_failed: ['rate<0.01'],  // error rate under 1%
};
