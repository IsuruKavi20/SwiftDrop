#!/usr/bin/env bash
# Puts a fresh ECR pull token into the Secret "ecr-pull" (namespace swiftdrop).
# Uses the server's IAM role (swiftdrop-ec2 has AmazonEC2ContainerRegistryReadOnly): no keys stored anywhere.
# Runs every 6 hours from a systemd timer (tokens last 12 hours).
set -euo pipefail
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
REGION="ap-south-1"
REGISTRY="637675605734.dkr.ecr.${REGION}.amazonaws.com"

TOKEN="$(aws ecr get-login-password --region "$REGION")"
kubectl -n swiftdrop create secret docker-registry ecr-pull \
  --docker-server="$REGISTRY" --docker-username=AWS --docker-password="$TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
echo "ecr-pull refreshed $(date -u +%FT%TZ)"
