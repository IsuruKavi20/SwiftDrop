#!/usr/bin/env bash
# One-time setup of the SwiftDrop server (EC2 swiftdrop-k3s, Amazon Linux 2023, arm64).
# Run it as root in an SSM session:   sudo bash install.sh
# Safe to run again: every step checks what is already there.
set -euo pipefail

K3S_VERSION="v1.37.2+k3s1"          # pinned: upgrades are a deliberate change, not a surprise
CERT_MANAGER_VERSION="v1.21.3"
NAMESPACE="swiftdrop"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "== 0. 2 GB swap (safety net: the t4g.small has only 2 GB of memory) =="
if ! swapon --show | grep -q /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q /swapfile /etc/fstab || echo "/swapfile none swap sw 0 0" >> /etc/fstab
  # Use swap only when really needed, keep the apps in fast memory
  sysctl -q vm.swappiness=10 && echo "vm.swappiness=10" > /etc/sysctl.d/90-swappiness.conf
fi

echo "== 1. k3s (Kubernetes) with Traefik =="
if ! command -v k3s >/dev/null; then
  # --secrets-encryption: Kubernetes Secrets are encrypted on disk
  # --write-kubeconfig-mode 600: only root can read the admin config
  # fail-swap-on=false: allow the swap file above
  curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="$K3S_VERSION" sh -s - server \
    --secrets-encryption --write-kubeconfig-mode 600 --kubelet-arg=fail-swap-on=false
fi
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
until kubectl get nodes 2>/dev/null | grep -q " Ready"; do sleep 3; done
kubectl get nodes

echo "== 2. Helm =="
if ! command -v helm >/dev/null; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi

echo "== 3. cert-manager (free HTTPS certificates from Let's Encrypt) =="
helm repo add jetstack https://charts.jetstack.io --force-update >/dev/null
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --version "$CERT_MANAGER_VERSION" \
  --set crds.enabled=true \
  --set resources.requests.memory=32Mi --set webhook.resources.requests.memory=16Mi \
  --set cainjector.resources.requests.memory=32Mi --wait
kubectl apply -f "$HERE/cluster-issuers.yaml"

echo "== 4. Namespace =="
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

echo "== 5. ECR login refresher (ECR tokens expire after 12 hours) =="
install -m 0755 "$HERE/ecr-login.sh" /usr/local/bin/swiftdrop-ecr-login
cat > /etc/systemd/system/swiftdrop-ecr-login.service <<'UNIT'
[Unit]
Description=Refresh the ECR pull secret for SwiftDrop
After=k3s.service
[Service]
Type=oneshot
ExecStart=/usr/local/bin/swiftdrop-ecr-login
UNIT
cat > /etc/systemd/system/swiftdrop-ecr-login.timer <<'UNIT'
[Unit]
Description=Refresh the ECR pull secret every 6 hours
[Timer]
OnBootSec=1min
OnUnitActiveSec=6h
[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now swiftdrop-ecr-login.timer
/usr/local/bin/swiftdrop-ecr-login

echo "== Done. Next: sync-secrets.sh, then the first deploy (see docs/DEPLOY.md) =="
