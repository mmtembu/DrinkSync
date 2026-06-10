/**
 * DrinkSync Load Test
 *
 * Simulates event traffic patterns against the DrinkSync API.
 *
 * Run:
 *   k6 run --env BASE_URL=http://154.66.197.77 load-tests/k6-load-test.js
 *
 * Run specific scenario:
 *   k6 run --env BASE_URL=http://154.66.197.77 --env SCENARIO=smoke load-tests/k6-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const orderLatency = new Trend('order_latency');

// Base URL - override with: k6 run --env BASE_URL=http://154.66.197.77
const BASE_URL = __ENV.BASE_URL || 'http://dev.manga-corp.co.za';

// Test scenarios
export const options = {
  scenarios: {
    // Smoke test: 1 VU for 30s (sanity check)
    smoke: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
      gracefulStop: '5s',
      tags: { scenario: 'smoke' },
      exec: 'smokeTest',
    },
    // Load test: ramp to 20 VUs over 1min, hold 2min, ramp down
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 0 },
      ],
      startTime: '35s',
      gracefulStop: '10s',
      tags: { scenario: 'load' },
      exec: 'loadTest',
    },
    // Stress test: ramp to 50 VUs over 2min, hold 3min, ramp down
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 0 },
      ],
      startTime: '5m',
      gracefulStop: '15s',
      tags: { scenario: 'stress' },
      exec: 'stressTest',
    },
  },
  thresholds: {
    // P95 response time under 500ms
    http_req_duration: ['p(95)<500'],
    // Error rate under 1%
    errors: ['rate<0.01'],
    // Order-specific latency
    order_latency: ['p(95)<800'],
  },
};

// ============================================
// Smoke Test: verify system is alive
// ============================================
export function smokeTest() {
  // GET /api/stations/1 (menu browsing)
  const stationRes = http.get(`${BASE_URL}/api/stations/1`);
  check(stationRes, {
    'station returns 200 or 404': (r) => r.status === 200 || r.status === 404,
    'station responds within 500ms': (r) => r.timings.duration < 500,
  });
  errorRate.add(stationRes.status >= 500);

  sleep(1);
}

// ============================================
// Load Test: simulate normal customer flow
// ============================================
export function loadTest() {
  const headers = { 'Content-Type': 'application/json' };

  // 1. GET /api/stations/1 (menu browsing)
  const stationRes = http.get(`${BASE_URL}/api/stations/1`);
  check(stationRes, {
    'station accessible': (r) => r.status < 500,
  });
  errorRate.add(stationRes.status >= 500);
  sleep(0.5);

  // 2. POST /api/stations/1/orders (order creation)
  const orderPayload = JSON.stringify({
    items: [
      { type: 'PREMADE', premadeItemId: 1, quantity: 1 },
    ],
  });

  const startTime = Date.now();
  const orderRes = http.post(`${BASE_URL}/api/stations/1/orders`, orderPayload, { headers });
  const duration = Date.now() - startTime;
  orderLatency.add(duration);

  check(orderRes, {
    'order created or rejected gracefully': (r) => r.status < 500,
  });
  errorRate.add(orderRes.status >= 500);
  sleep(0.5);

  // 3. GET /api/orders/1 (order status check)
  const orderStatusRes = http.get(`${BASE_URL}/api/orders/1`);
  check(orderStatusRes, {
    'order status accessible': (r) => r.status < 500,
  });
  errorRate.add(orderStatusRes.status >= 500);
  sleep(0.5);

  // 4. POST /api/orders/1/checkout (checkout)
  const checkoutRes = http.post(`${BASE_URL}/api/orders/1/checkout`, '{}', { headers });
  check(checkoutRes, {
    'checkout handled': (r) => r.status < 500,
  });
  errorRate.add(checkoutRes.status >= 500);

  sleep(1);
}

// ============================================
// Stress Test: push beyond normal capacity
// ============================================
export function stressTest() {
  const headers = { 'Content-Type': 'application/json' };

  // Rapid-fire requests simulating peak event traffic
  const responses = http.batch([
    ['GET', `${BASE_URL}/api/stations/1`],
    ['GET', `${BASE_URL}/api/orders/1`],
  ]);

  responses.forEach((res) => {
    errorRate.add(res.status >= 500);
  });

  // Place order under stress
  const orderPayload = JSON.stringify({
    items: [
      { type: 'PREMADE', premadeItemId: 1, quantity: 2 },
    ],
  });

  const startTime = Date.now();
  const orderRes = http.post(`${BASE_URL}/api/stations/1/orders`, orderPayload, { headers });
  const duration = Date.now() - startTime;
  orderLatency.add(duration);

  check(orderRes, {
    'order handled under stress': (r) => r.status < 500,
  });
  errorRate.add(orderRes.status >= 500);

  // Checkout under stress
  const checkoutRes = http.post(`${BASE_URL}/api/orders/1/checkout`, '{}', { headers });
  check(checkoutRes, {
    'checkout handled under stress': (r) => r.status < 500,
  });
  errorRate.add(checkoutRes.status >= 500);

  sleep(0.3);
}
