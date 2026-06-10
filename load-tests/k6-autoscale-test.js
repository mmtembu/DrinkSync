import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const requestCount = new Counter('total_requests');

// Base URL - override with: k6 run --env BASE_URL=http://154.66.197.77
const BASE_URL = __ENV.BASE_URL || 'http://dev.manga-corp.co.za';

// Autoscale test: ramp up to trigger HPA, then ramp down
// Purpose: verify pods scale from 1 -> 2 under load, then back to 1
export const options = {
  scenarios: {
    autoscale: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        // Ramp up gradually to trigger HPA (CPU > 70%)
        { duration: '1m', target: 10 },
        { duration: '1m', target: 20 },
        { duration: '1m', target: 30 },
        // Hold at peak to allow HPA to scale up
        { duration: '2m', target: 30 },
        // Ramp down to allow scale-down
        { duration: '1m', target: 5 },
        { duration: '1m', target: 0 },
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'], // More lenient during scaling
    errors: ['rate<0.05'], // Allow 5% errors during scaling events
  },
};

export default function () {
  const headers = { 'Content-Type': 'application/json' };

  // Mix of read and write operations to generate CPU load
  const scenario = Math.random();

  if (scenario < 0.4) {
    // 40%: Read station info (lightweight)
    const res = http.get(`${BASE_URL}/api/stations/1`);
    check(res, { 'station ok': (r) => r.status < 500 });
    errorRate.add(res.status >= 500);
    requestCount.add(1);
  } else if (scenario < 0.7) {
    // 30%: Place orders (CPU-intensive)
    const orderPayload = JSON.stringify({
      stationId: 1,
      items: [
        { type: 'PREMADE', premadeItemId: 1, quantity: 1 },
        { type: 'PREMADE', premadeItemId: 2, quantity: 1 },
      ],
    });

    const res = http.post(`${BASE_URL}/api/orders`, orderPayload, { headers });
    check(res, { 'order ok': (r) => r.status < 500 });
    errorRate.add(res.status >= 500);
    requestCount.add(1);
  } else if (scenario < 0.9) {
    // 20%: Get orders (moderate)
    const res = http.get(`${BASE_URL}/api/orders/1`);
    check(res, { 'order fetch ok': (r) => r.status < 500 });
    errorRate.add(res.status >= 500);
    requestCount.add(1);
  } else {
    // 10%: Health check
    const res = http.get(`${BASE_URL}/actuator/health`);
    check(res, { 'health ok': (r) => r.status === 200 });
    errorRate.add(res.status >= 500);
    requestCount.add(1);
  }

  // Short sleep to simulate realistic user behavior
  sleep(0.2 + Math.random() * 0.3);
}

// Lifecycle hooks for monitoring
export function handleSummary(data) {
  const summary = {
    totalRequests: data.metrics.total_requests ? data.metrics.total_requests.values.count : 0,
    errorRate: data.metrics.errors ? data.metrics.errors.values.rate : 0,
    p95Latency: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
    medianLatency: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(50)'] : 0,
  };

  console.log('\n========================================');
  console.log('  AUTOSCALE TEST SUMMARY');
  console.log('========================================');
  console.log(`  Total Requests: ${summary.totalRequests}`);
  console.log(`  Error Rate: ${(summary.errorRate * 100).toFixed(2)}%`);
  console.log(`  P95 Latency: ${summary.p95Latency.toFixed(0)}ms`);
  console.log(`  Median Latency: ${summary.medianLatency.toFixed(0)}ms`);
  console.log('========================================');
  console.log('\n  TIP: While this test runs, monitor HPA:');
  console.log('  kubectl get hpa -n drinksync -w');
  console.log('  kubectl get pods -n drinksync -w');
  console.log('========================================\n');

  return {
    stdout: JSON.stringify(summary, null, 2),
  };
}
