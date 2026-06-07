# Load Tests

Performance and autoscaling tests for DrinkSync using [k6](https://k6.io/).

## Prerequisites

### Install k6

```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Docker
docker run --rm -i grafana/k6 run - <script.js
```

## Running Tests

### All scenarios (smoke → load → stress)

```bash
k6 run --env BASE_URL=http://154.66.197.77 load-tests/k6-load-test.js
```

### Against local environment

```bash
k6 run --env BASE_URL=http://localhost:8080 load-tests/k6-load-test.js
```

### Autoscale test (triggers HPA)

```bash
k6 run --env BASE_URL=http://154.66.197.77 load-tests/k6-autoscale-test.js
```

## Scenarios

### k6-load-test.js

| Scenario | VUs | Duration | Purpose |
|----------|-----|----------|---------|
| Smoke | 1 | 30s | Sanity check — verify system is alive |
| Load | ramp to 20 | 1m ramp + 2m hold + 30s down | Normal expected traffic |
| Stress | ramp to 50 | 2m ramp + 3m hold + 1m down | Push beyond capacity |

**Thresholds:**
- P95 response time < 500ms
- Error rate < 1%

### k6-autoscale-test.js

Ramps from 1 to 30 VUs over 3 minutes, holds for 2 minutes, then ramps down. Designed to trigger the HPA and verify backend scales from 1→2 pods.

## Monitoring During Tests

While load tests run, monitor the cluster in another terminal:

```bash
# Watch HPA scaling decisions
kubectl get hpa -n drinksync -w

# Watch pod scaling
kubectl get pods -n drinksync -w

# Watch resource usage (requires metrics-server)
kubectl top pods -n drinksync

# Check backend logs for errors
kubectl logs -f deployment/backend -n drinksync --tail=50
```

## Expected Behavior

Under the **load** scenario (20 VUs):
- Backend should handle requests with P95 < 500ms
- HPA may or may not trigger (depends on CPU usage)
- No pod restarts

Under the **stress** scenario (50 VUs):
- Backend should scale from 1→2 pods (HPA triggers at 70% CPU)
- P95 latency may increase during scaling
- Error rate should stay below 1%
- After load decreases, pods scale back to 1 (after 5min cooldown)

## Interpreting Results

### Healthy:
- P95 latency under 500ms for normal load
- Error rate below 1%
- HPA scales to 2 pods under stress

### Warning signs:
- P95 latency above 1000ms → backend may need more memory
- Error rate above 5% → check pod logs for OOM kills
- HPA not scaling → verify metrics-server is running: `kubectl get pods -n kube-system | grep metrics`
- Pods restarting → likely OOM, check: `kubectl describe pod <name> -n drinksync`
