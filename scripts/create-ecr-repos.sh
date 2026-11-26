#!/usr/bin/env bash
# Creates the four ShopSphere ECR repositories (idempotent: safe to re-run):
# shopsphere-user-service, shopsphere-product-service, shopsphere-order-service, shopsphere-frontend
#
# Usage:
#   export AWS_REGION=<your-region>     # e.g. the region of your EKS cluster
#   ./scripts/create-ecr-repos.sh
#
# Settings applied to every repository:
#   - immutable tags     : a tag such as 1a2b3c4d-b7 can never be overwritten
#   - scan on push       : basic vulnerability scan for every pushed image
#   - lifecycle policy   : keep only the 10 newest images
set -euo pipefail

: "${AWS_REGION:?Set AWS_REGION first, e.g. export AWS_REGION=<your-region>}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_FILE="${SCRIPT_DIR}/../aws/ecr-lifecycle-policy.json"

for service in user-service product-service order-service frontend; do
  repo="shopsphere-${service}"

  if aws ecr describe-repositories --repository-names "${repo}" --region "${AWS_REGION}" >/dev/null 2>&1; then
    echo "exists : ${repo}"
  else
    aws ecr create-repository \
      --repository-name "${repo}" \
      --image-tag-mutability IMMUTABLE \
      --image-scanning-configuration scanOnPush=true \
      --region "${AWS_REGION}" \
      --query 'repository.repositoryUri' --output text
    echo "created: ${repo}"
  fi

  aws ecr put-lifecycle-policy \
    --repository-name "${repo}" \
    --lifecycle-policy-text "file://${POLICY_FILE}" \
    --region "${AWS_REGION}" >/dev/null
done

echo "Done. Repositories in ${AWS_REGION}:"
aws ecr describe-repositories --region "${AWS_REGION}" \
  --query "repositories[?starts_with(repositoryName, 'shopsphere-')].repositoryUri" --output text
