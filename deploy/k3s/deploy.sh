#!/usr/bin/env bash
# Deploys one backend version (the git commit CI built). Called by GitHub Actions through SSM Run Command,
# or by hand:   sudo bash deploy.sh <git-commit-sha>
# The certificate issuer is a server setting in /etc/swiftdrop/cluster-issuer (default: letsencrypt-staging).
# Switch to real certificates once:   echo letsencrypt-prod | sudo tee /etc/swiftdrop/cluster-issuer
set -euo pipefail
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
TAG="${1:?usage: deploy.sh <image-tag>}"
ISSUER="$(cat /etc/swiftdrop/cluster-issuer 2>/dev/null || echo letsencrypt-staging)"
[[ "$ISSUER" =~ ^letsencrypt-(staging|prod)$ ]] || { echo "bad issuer in /etc/swiftdrop/cluster-issuer"; exit 1; }
[[ "$TAG" =~ ^[0-9a-f]{7,40}$ ]] || { echo "bad image tag: $TAG"; exit 1; }

CHART_DIR="$(cd "$(dirname "$0")/../helm/swiftdrop" && pwd)"
# --atomic: if the new version doesn't become healthy in 6 minutes, Helm rolls back to the last good one
helm upgrade --install swiftdrop "$CHART_DIR" --namespace swiftdrop \
  --set backend.image.tag="$TAG" --set ingress.clusterIssuer="$ISSUER" \
  --atomic --timeout 6m --history-max 5
kubectl -n swiftdrop get pods
