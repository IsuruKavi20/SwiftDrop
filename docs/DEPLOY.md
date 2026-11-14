# Deploy: how SwiftDrop runs on AWS

```
Browser ──https──> app.getswiftdrop.me  (CloudFront → S3: the React app)
        ──https──> api.getswiftdrop.me  (Elastic IP → EC2 t4g.small → k3s)
                                          ├─ Traefik (HTTPS, Let's Encrypt via cert-manager)
                                          ├─ backend pod (Spring Boot, image from ECR)
                                          └─ RabbitMQ pod
                                        backend ──> RDS Postgres (private)   backend ──> auth.getswiftdrop.me
```

**After setup, deploying is automatic:** merge to `main` → GitHub Actions "Deploy" → image to ECR → Helm upgrade on
the server (through SSM, no SSH) → frontend to S3 + CloudFront. A version that doesn't become healthy is rolled back
automatically (`helm --atomic`).

## Files
| Path | What |
|---|---|
| `backend/Dockerfile` | the backend image (JDK build → small JRE runtime, non-root user) |
| `deploy/helm/swiftdrop/` | Kubernetes setup: backend, HTTPS ingress, RabbitMQ |
| `deploy/k3s/install.sh` | one-time server setup (swap, k3s, Helm, cert-manager, ECR login timer) |
| `deploy/k3s/sync-secrets.sh` | copies secrets from SSM into Kubernetes |
| `deploy/k3s/deploy.sh` | deploys one version (used by CI, or by hand) |
| `deploy/aws/*.json` | IAM policies for CI and the server |
| `.github/workflows/deploy.yml` | the automatic deploy |

## One-time setup (Isuru, in this order)
Everything uses your admin login (`aws login`). Nothing here prints a secret.

**1. Server may read production secrets.** Add an inline policy to the role `swiftdrop-ec2`:
```powershell
aws iam put-role-policy --role-name swiftdrop-ec2 --policy-name read-prod-secrets --policy-document file://deploy/aws/ec2-read-prod-secrets-policy.json
```

**2. RabbitMQ password in SSM** (random, never typed or shown):
```powershell
$p = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 32 | % {[char]$_}); aws ssm put-parameter --name /swiftdrop/prod/rabbitmq/password --type SecureString --value $p; Remove-Variable p
```

**3. Install the server** (SSM session: `aws ssm start-session --target i-03c8bce686311da77`):
```bash
sudo dnf install -y git tar
sudo git clone --depth 1 https://github.com/Tharusha-Heshan/SwiftDrop /opt/swiftdrop/release
sudo bash /opt/swiftdrop/release/deploy/k3s/install.sh
sudo bash /opt/swiftdrop/release/deploy/k3s/sync-secrets.sh
```

**4. DNS:** Namecheap → A record `api` → `15.252.58.175`. Check: `nslookup api.getswiftdrop.me`.

**5. GitHub can log in to AWS (OIDC), no keys:**
```powershell
aws iam create-open-id-connect-provider --url https://token.actions.githubusercontent.com --client-id-list sts.amazonaws.com
aws iam create-role --role-name swiftdrop-github-deploy --assume-role-policy-document file://deploy/aws/github-deploy-trust.json --max-session-duration 3600
aws iam put-role-policy --role-name swiftdrop-github-deploy --policy-name deploy --policy-document file://deploy/aws/github-deploy-policy.json
```

**6. GitHub settings** (Tharusha, repo owner): Settings → Environments → New `production`
(Deployment branches: `main` only). In it add **Variables** (not secrets, none of these are secret):
| Name | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::637675605734:role/swiftdrop-github-deploy` |
| `EC2_INSTANCE_ID` | `i-03c8bce686311da77` |
| `FRONTEND_BUCKET` | `swiftdrop-frontend-637675605734-ap-south-1-an` |
| `CLOUDFRONT_DISTRIBUTION_ID` | `EVVC5QIERWH04` |

Also a **repository** variable (Settings → Secrets and variables → Actions → Variables): `DEPLOY_ENABLED` = `true`.
Until it exists, the Deploy workflow is skipped (so merges before the setup don't fail).

**7. First deploy with a TEST certificate:** Actions → Deploy → Run workflow. Then on the server:
```bash
sudo k3s kubectl -n swiftdrop get pods,certificate
curl -k https://api.getswiftdrop.me/api/customers/me   # 401 JSON = working (the -k accepts the test cert)
```
**8. Real certificate:** when step 7 works, switch the server to real certificates and deploy again
(Actions → Deploy → Run workflow):
```bash
sudo mkdir -p /etc/swiftdrop && echo letsencrypt-prod | sudo tee /etc/swiftdrop/cluster-issuer
```

**9. Auth service:** add `https://app.getswiftdrop.me` as a frontend (redirect + CORS) in the auth service, and the
push URL `https://api.getswiftdrop.me/internal/identity-events` (roadmap item H).

## Everyday commands (on the server)
```bash
sudo k3s kubectl -n swiftdrop get pods                     # is it running?
sudo k3s kubectl -n swiftdrop logs deploy/swiftdrop-backend --tail=100
sudo helm -n swiftdrop history swiftdrop                   # deployed versions
sudo helm -n swiftdrop rollback swiftdrop                  # back to the previous version
sudo k3s kubectl -n swiftdrop port-forward svc/swiftdrop-rabbitmq 15672 # RabbitMQ UI (then SSM port forward)
```

## Limits (t4g.small, 2 GB)
- One backend copy; each deploy has ~30–60 s downtime (strategy `Recreate`). More memory → `RollingUpdate`.
- Swap 2 GB is a safety net; if pods restart with `OOMKilled`, upgrade to t4g.medium (~$0.80/day).
- Costs: see `learning/PLAN.md` (EC2 + EIP + RDS ≈ $1.3/day from the $100 credit).
