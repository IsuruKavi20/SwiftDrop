#!/usr/bin/env bash
# Copies the production secrets from AWS SSM Parameter Store into the Kubernetes Secret "swiftdrop-secrets".
# Run as root on the server after changing a secret in SSM, then restart the backend:
#   sudo bash sync-secrets.sh && sudo k3s kubectl -n swiftdrop rollout restart deploy/swiftdrop-backend
# Nothing is printed or written to disk: values go straight from SSM into the (encrypted) Secret.
set -euo pipefail
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
REGION="ap-south-1"

get() { aws ssm get-parameter --region "$REGION" --with-decryption --name "$1" --query Parameter.Value --output text; }
# Optional parameters: empty when missing
opt() { get "$1" 2>/dev/null || true; }

kubectl -n swiftdrop create secret generic swiftdrop-secrets \
  --from-literal=DB_URL="$(get /swiftdrop/prod/db/url)" \
  --from-literal=DB_USERNAME="$(get /swiftdrop/prod/db/username)" \
  --from-literal=DB_PASSWORD="$(get /swiftdrop/prod/db/password)" \
  --from-literal=RABBITMQ_USERNAME="swiftdrop" \
  --from-literal=RABBITMQ_PASSWORD="$(get /swiftdrop/prod/rabbitmq/password)" \
  --from-literal=SWIFTDROP_IDENTITY_CLIENT_ID="$(opt /swiftdrop/prod/identity/client-id)" \
  --from-literal=SWIFTDROP_IDENTITY_CLIENT_SECRET="$(opt /swiftdrop/prod/identity/client-secret)" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
echo "swiftdrop-secrets updated from SSM"
