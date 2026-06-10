#!/bin/bash
# ============================================
# DrinkSync VPS Setup Script
# Target: Ubuntu 22.04 LTS, 1 vCPU, 2GB RAM
# Hostname: dev.manga-corp.co.za
# IP: 154.66.197.77
# ============================================
# This script is idempotent — safe to run multiple times.

set -euo pipefail

echo "=========================================="
echo "  DrinkSync VPS Setup"
echo "  Target: Ubuntu 22.04 LTS (k3s)"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if running as root
if [ "$EUID" -ne 0 ]; then
  log_error "Please run as root (sudo ./setup-vps.sh)"
  exit 1
fi

# ============================================
# 1. System updates
# ============================================
log_info "Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq

# ============================================
# 2. Install k3s (lightweight Kubernetes)
# ============================================
log_info "Installing k3s..."
if command -v k3s &> /dev/null; then
  log_warn "k3s already installed, skipping..."
else
  curl -sfL https://get.k3s.io | sh -s - \
    --write-kubeconfig-mode 644 \
    --disable metrics-server \
    --kubelet-arg="--max-pods=30"

  # Wait for k3s to be ready
  log_info "Waiting for k3s to be ready..."
  sleep 15
  kubectl wait --for=condition=Ready node --all --timeout=120s
fi

# ============================================
# 3. Configure kubectl for non-root user
# ============================================
log_info "Configuring kubectl access..."
ACTUAL_USER="${SUDO_USER:-$USER}"
ACTUAL_HOME=$(eval echo "~$ACTUAL_USER")

mkdir -p "$ACTUAL_HOME/.kube"
cp /etc/rancher/k3s/k3s.yaml "$ACTUAL_HOME/.kube/config"
chown -R "$ACTUAL_USER:$ACTUAL_USER" "$ACTUAL_HOME/.kube"
chmod 600 "$ACTUAL_HOME/.kube/config"

# Symlink for root convenience
ln -sf /etc/rancher/k3s/k3s.yaml /root/.kube/config 2>/dev/null || true

# ============================================
# 4. Install metrics-server (needed for HPA)
# ============================================
log_info "Installing metrics-server for HPA support..."
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml 2>/dev/null || true

# Patch metrics-server for single-node k3s (insecure TLS to kubelet)
sleep 5
kubectl patch deployment metrics-server -n kube-system \
  --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]' 2>/dev/null || true

# ============================================
# 5. Create namespace
# ============================================
log_info "Creating drinksync namespace..."
kubectl create namespace drinksync --dry-run=client -o yaml | kubectl apply -f -

# ============================================
# 6. Configure swap (safety net for 2GB RAM)
# ============================================
log_info "Configuring swap as safety net..."
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  log_info "1GB swap file created."
else
  log_warn "Swap already configured."
fi

# ============================================
# 7. Apply Kubernetes manifests
# ============================================
log_info "Applying Kubernetes manifests..."

# Check if k8s directory exists
K8S_DIR="${K8S_MANIFESTS_DIR:-./k8s}"
if [ ! -d "$K8S_DIR" ]; then
  log_error "k8s/ directory not found at $K8S_DIR"
  log_error "Please clone the repo or set K8S_MANIFESTS_DIR."
  exit 1
fi

# Apply in order (namespace first, then config, then workloads)
kubectl apply -f "$K8S_DIR/namespace.yaml"
kubectl apply -f "$K8S_DIR/configmap.yaml"
kubectl apply -f "$K8S_DIR/secrets.yaml" 2>/dev/null || log_warn "Secrets may need manual update (ghcr-secret placeholder)"
kubectl apply -f "$K8S_DIR/services.yaml"
kubectl apply -f "$K8S_DIR/postgres.yaml"

# Wait for postgres to be ready before deploying backend
log_info "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=Ready pod -l app=postgres -n drinksync --timeout=120s || sleep 15

kubectl apply -f "$K8S_DIR/backend.yaml"
kubectl apply -f "$K8S_DIR/frontend.yaml"
kubectl apply -f "$K8S_DIR/ingress.yaml"
kubectl apply -f "$K8S_DIR/hpa.yaml"
kubectl apply -f "$K8S_DIR/db-health-cronjob.yaml"

# ============================================
# 8. Print status and next steps
# ============================================
echo ""
echo "=========================================="
echo "  Setup Complete!"
echo "=========================================="
echo ""
log_info "Cluster status:"
kubectl get nodes
echo ""
log_info "Pods in drinksync namespace:"
kubectl get pods -n drinksync
echo ""
log_info "Services:"
kubectl get svc -n drinksync
echo ""

echo "=========================================="
echo "  NEXT STEPS"
echo "=========================================="
echo ""
echo "1. Create the GHCR pull secret (required for image pulls):"
echo ""
echo "   kubectl create secret docker-registry ghcr-secret \\"
echo "     --docker-server=ghcr.io \\"
echo "     --docker-username=mmtembu \\"
echo "     --docker-password=YOUR_GITHUB_PAT \\"
echo "     --namespace=drinksync \\"
echo "     --dry-run=client -o yaml | kubectl apply -f -"
echo ""
echo "2. Update database and app secrets with real values:"
echo ""
echo "   DB_PASSWORD=\$(openssl rand -base64 24)"
echo "   JWT_SECRET=\$(openssl rand -base64 48)"
echo ""
echo "   kubectl create secret generic postgres-secret \\"
echo "     --from-literal=POSTGRES_DB=smarteventbar \\"
echo "     --from-literal=POSTGRES_USER=smarteventbar \\"
echo "     --from-literal=POSTGRES_PASSWORD=\"\$DB_PASSWORD\" \\"
echo "     -n drinksync --dry-run=client -o yaml | kubectl apply -f -"
echo ""
echo "   kubectl create secret generic app-secret \\"
echo "     --from-literal=SPRING_DATASOURCE_USERNAME=smarteventbar \\"
echo "     --from-literal=SPRING_DATASOURCE_PASSWORD=\"\$DB_PASSWORD\" \\"
echo "     --from-literal=JWT_SECRET=\"\$JWT_SECRET\" \\"
echo "     -n drinksync --dry-run=client -o yaml | kubectl apply -f -"
echo ""
echo "3. Set up GitHub Actions SSH key:"
echo ""
echo "   # On VPS: generate a deploy key"
echo "   ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N ''"
echo "   cat ~/.ssh/github_deploy.pub >> ~/.ssh/authorized_keys"
echo ""
echo "   # Copy private key to GitHub Secrets as VPS_SSH_KEY"
echo "   cat ~/.ssh/github_deploy"
echo ""
echo "4. Verify the deployment:"
echo ""
echo "   kubectl get pods -n drinksync"
echo "   curl http://154.66.197.77/actuator/health"
echo "   curl http://dev.manga-corp.co.za/"
echo ""
echo "=========================================="
