#!/usr/bin/env bash
set -euo pipefail

required=(MYSQL_ROOT_PASSWORD MYSQL_PASSWORD JWT_SECRET SERVICE_API_KEY SHOPSPHERE_ADMIN_NAME SHOPSPHERE_ADMIN_EMAIL SHOPSPHERE_ADMIN_PASSWORD)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Set ${name} in the environment before running this script." >&2
    exit 2
  fi
done

if (( ${#JWT_SECRET} < 32 )); then
  echo "JWT_SECRET must be at least 32 bytes." >&2
  exit 2
fi
if (( ${#SERVICE_API_KEY} < 32 )); then
  echo "SERVICE_API_KEY must be at least 32 bytes." >&2
  exit 2
fi
if (( ${#SHOPSPHERE_ADMIN_PASSWORD} < 10 )); then
  echo "SHOPSPHERE_ADMIN_PASSWORD must be at least 10 characters." >&2
  exit 2
fi

kubectl create secret generic mysql-credentials \
  --namespace shopsphere \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  --from-literal=MYSQL_USER=shopsphere \
  --from-literal=MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic shopsphere-app-secrets \
  --namespace shopsphere \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=SERVICE_API_KEY="$SERVICE_API_KEY" \
  --from-literal=SHOPSPHERE_ADMIN_NAME="$SHOPSPHERE_ADMIN_NAME" \
  --from-literal=SHOPSPHERE_ADMIN_EMAIL="$SHOPSPHERE_ADMIN_EMAIL" \
  --from-literal=SHOPSPHERE_ADMIN_PASSWORD="$SHOPSPHERE_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Kubernetes application and MySQL secrets are up to date in namespace shopsphere."
