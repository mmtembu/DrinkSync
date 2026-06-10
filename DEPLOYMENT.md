# DrinkSync Deployment Guide

Production deployment on a single-node k3s cluster (Ubuntu 22.04 VPS).

## Infrastructure Overview

| Component | Memory Limit | CPU Limit | Notes |
|-----------|-------------|-----------|-------|
| k3s system + Traefik | ~500MB | — | System overhead |
| PostgreSQL | 256MB | 200m | StatefulSet, 5Gi persistent volume |
| Backend (Spring Boot) | 512MB | 500m | HPA: 1-2 replicas |
| Frontend (nginx) | 64MB | 100m | Static files, very lightweight |
| System overhead | ~200MB | — | OS, buffers, etc. |
| **Total (1 backend pod)** | **~1.5GB** | — | Leaves ~500MB for scaling |

When HPA scales the backend to 2 pods, total usage reaches ~2GB (swap provides safety net).

## Prerequisites

- Ubuntu 22.04 LTS VPS (1 vCPU, 2GB RAM, 50GB disk)
- IP: `154.66.197.77`
- Hostname: `dev.manga-corp.co.za`
- GitHub repository: `mmtembu/DrinkSync`
- GitHub Personal Access Token (PAT) with `read:packages` scope

## Quick Start

```bash
# 1. SSH into VPS
ssh root@154.66.197.77

# 2. Clone the repository
git clone https://github.com/mmtembu/DrinkSync.git /opt/drinksync
cd /opt/drinksync

# 3. Run setup (installs k3s, applies manifests)
chmod +x scripts/setup-vps.sh
sudo ./scripts/setup-vps.sh

# 4. Create GHCR pull secret
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=mmtembu \
  --docker-password=YOUR_GITHUB_PAT \
  --namespace=drinksync

# 5. Set real secrets
DB_PASSWORD=$(openssl rand -base64 24)
JWT_SECRET=$(openssl rand -base64 48)

kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_DB=smarteventbar \
  --from-literal=POSTGRES_USER=smarteventbar \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  -n drinksync --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic app-secret \
  --from-literal=SPRING_DATASOURCE_USERNAME=smarteventbar \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  -n drinksync --dry-run=client -o yaml | kubectl apply -f -

# 6. Restart pods to pick up secrets
kubectl rollout restart statefulset/postgres -n drinksync
kubectl rollout restart deployment/backend -n drinksync
```

## GitHub Secrets

Configure these in your GitHub repository (Settings → Secrets and variables → Actions):

| Secret | Value | Description |
|--------|-------|-------------|
| `VPS_HOST` | `154.66.197.77` | VPS IP address |
| `VPS_USER` | `root` (or deploy user) | SSH username |
| `VPS_SSH_KEY` | Private key content | SSH private key for VPS access |

> `GITHUB_TOKEN` is automatically available for pushing to ghcr.io.

### Setting up the SSH key

```bash
# On VPS: generate a deploy key
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N ''
cat ~/.ssh/github_deploy.pub >> ~/.ssh/authorized_keys

# Copy the private key — paste this into GitHub Secrets as VPS_SSH_KEY
cat ~/.ssh/github_deploy
```

## CI/CD Pipeline

### On Pull Request (CI)
- Runs backend tests: `./mvnw verify -DskipITs`
- Runs frontend lint + tests: `npm run lint && npm run test`

### On Push to Main (CD)
1. **Test** — Same as CI
2. **Build & Push** — Builds Docker images, pushes to `ghcr.io/mmtembu/drinksync-backend` and `ghcr.io/mmtembu/drinksync-frontend`
3. **Deploy** — SSHs into VPS, updates deployments with new image tag, waits for rollout

## Monitoring

### Pod status and resource usage

```bash
kubectl get pods -n drinksync -o wide
kubectl top pods -n drinksync
kubectl top nodes
kubectl get hpa -n drinksync
```

### Logs

```bash
# Backend logs (follow)
kubectl logs -f deployment/backend -n drinksync

# Frontend logs
kubectl logs -f deployment/frontend -n drinksync

# PostgreSQL logs
kubectl logs -f statefulset/postgres -n drinksync

# Recent events
kubectl get events -n drinksync --sort-by='.lastTimestamp' | tail -20
```

## Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/backend -n drinksync
kubectl rollout undo deployment/frontend -n drinksync

# Rollback to specific revision
kubectl rollout undo deployment/backend -n drinksync --to-revision=2

# Check rollout history
kubectl rollout history deployment/backend -n drinksync
```

## Manual Deployment

```bash
# On VPS — deploy a specific commit
IMAGE_TAG=abc1234

kubectl set image deployment/backend \
  backend=ghcr.io/mmtembu/drinksync-backend:$IMAGE_TAG \
  -n drinksync

kubectl set image deployment/frontend \
  frontend=ghcr.io/mmtembu/drinksync-frontend:$IMAGE_TAG \
  -n drinksync

kubectl rollout status deployment/backend -n drinksync --timeout=120s
kubectl rollout status deployment/frontend -n drinksync --timeout=60s
```

## Troubleshooting

### Pod stuck in CrashLoopBackOff

```bash
kubectl logs <pod-name> -n drinksync --previous
kubectl describe pod <pod-name> -n drinksync
```

Common causes:
- Database not ready (backend starts before postgres)
- Wrong secrets/config values
- OOM killed (check resource limits)

### OOM Killed

```bash
kubectl describe pod <pod-name> -n drinksync | grep -A5 "Last State"
```

Solutions:
- Check JVM heap settings (-Xmx384m is the limit)
- Look for memory leaks in application logs
- Temporarily increase limits if needed

### Image pull errors (ErrImagePull)

```bash
# Verify pull secret exists
kubectl get secret ghcr-secret -n drinksync

# Recreate if needed
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=mmtembu \
  --docker-password=$GHCR_TOKEN \
  --namespace=drinksync \
  --dry-run=client -o yaml | kubectl apply -f -
```

### Ingress not routing traffic

```bash
kubectl get ingressroute -n drinksync
kubectl logs -n kube-system -l app.kubernetes.io/name=traefik --tail=50
kubectl get endpoints -n drinksync
```

### Database connection refused

```bash
kubectl get pods -l app=postgres -n drinksync
kubectl logs statefulset/postgres -n drinksync
kubectl exec -it deployment/backend -n drinksync -- \
  wget -qO- http://localhost:8080/actuator/health
```

## Resource Allocation Breakdown

For a 2GB RAM VPS, here's how memory is allocated:

```
┌─────────────────────────────────────────────┐
│ Total RAM: 2048 MB                          │
├─────────────────────────────────────────────┤
│ k3s + Traefik + system:     ~500 MB         │
│ PostgreSQL (limit):          256 MB         │
│ Backend pod 1 (limit):       512 MB         │
│ Frontend pod (limit):         64 MB         │
│ OS overhead/buffers:         ~200 MB        │
├─────────────────────────────────────────────┤
│ Used (1 backend):          ~1,532 MB        │
│ Available for HPA scale:    ~516 MB         │
│ Backend pod 2 (if scaled):   512 MB         │
├─────────────────────────────────────────────┤
│ 1GB swap file: safety net for peak traffic  │
└─────────────────────────────────────────────┘
```

## Architecture Decisions

- **k3s over full k8s**: Saves ~500MB RAM, includes Traefik by default
- **Traefik IngressRoute**: Already bundled with k3s, no extra resource cost
- **PostgreSQL as StatefulSet**: Appropriate for single-node; for HA, use managed DB
- **HPA max 2 replicas**: 2GB RAM can't support more than 2 backend pods
- **JVM limits (-Xmx384m)**: Tight but sufficient for Spring Boot with G1GC
- **1GB swap**: Safety net to prevent OOM kills during traffic spikes
- **No TLS yet**: Add cert-manager + Let's Encrypt when DNS is configured
