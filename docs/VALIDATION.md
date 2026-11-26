# Validation report

## Current integration pass (2026-11-26)

This report reflects the final local integration changes on the service and frontend branches, plus the main deployment configuration. No branch was merged and nothing was pushed.

| Component | Command | Result |
|---|---|---|
| user-service | `mvn -B -ntp clean test` | 8 tests passed, 0 failed. Covers registration/login, roles, blocked token rejection, profile/password management, and trusted internal customer details. |
| user-service | `mvn -B -ntp clean package` | BUILD SUCCESS; tests passed and `target/user-service.jar` was produced. |
| product-service | `mvn -B -ntp clean test` | 9 tests passed, 0 failed. Covers catalog seed idempotence, authorization, inactive listing/reactivation, reservations, oversell prevention, and expiry recovery. |
| product-service | `mvn -B -ntp clean package` | BUILD SUCCESS; tests passed and `target/product-service.jar` was produced. |
| order-service | `mvn -B -ntp clean test` | 15 tests passed, 0 failed. Covers cart/order ownership, checkout idempotency, JWT roles, customer privacy, cancellation, and settlement recovery. Downstream user/product clients are mocked in these tests. |
| order-service | `mvn -B -ntp clean package` | BUILD SUCCESS; tests passed and `target/order-service.jar` was produced. |
| frontend | `npm.cmd test` | 12 tests passed, 0 failed, including inactive product reactivation and admin customer detail display. |
| frontend | `npm.cmd run build` | Vite production build succeeded. |
| Cross-branch contracts | `python scripts/check_consistency.py` | 245 checks passed, 0 failed across all five branches. The checker now validates routes, service URLs, secret references, API documentation, Compose services, and deployment structure. |
| Whitespace | `git diff --check` | Passed for the final changes at the time of commit. |

The Maven tests use Spring Boot/MockMvc with H2. User and product tests exercise their own controllers and persistence; order-service's calls to user-service and product-service are mocked. These are service-level/component tests, not live multi-service end-to-end tests. The admin order test verifies that customer details appear only in ADMIN responses. Product stock and order cancellation are tested separately; a running three-service workflow was not available.

## Deployment verification limits

| Area | Result |
|---|---|
| Docker and Docker Compose | Not run: Docker CLI/daemon is not installed in this environment. Docker image builds, Compose interpolation, startup ordering, health checks, and restart persistence remain unverified at runtime. Compose and Docker contexts were statically inspected and the cross-branch checker passed. |
| Kubernetes/EKS | Not run: `kubectl` is not installed, and no AWS cluster was accessed. YAML files were parsed by the consistency checker, but no cluster schema validation, pod startup, EBS provisioning, rollout, or load balancer was performed. No AWS deployment is claimed. |
| Jenkins | Jenkinsfiles were statically checked for branch guards, script paths, stages, tool usage, ECR repository names, credentials binding, and deployment manifests. No Jenkins controller or agent execution was available. |
| API smoke / browser E2E against real services | Not run. No live Java services or MySQL container were started. The customer and admin flows are covered by mocked frontend tests and backend component tests, but registration-to-checkout across live services remains outstanding. |
| Secret handling | No actual secrets were committed. `scripts/create-k8s-secrets.sh` creates the MySQL and `shopsphere-app-secrets` Kubernetes Secrets from caller-supplied environment variables. |

PyYAML was installed for the current user to run the existing checker. Docker, kubectl, Jenkins and AWS credentials were unavailable. Frontend `npm ci` emitted `EBADENGINE` warnings because the installed Node 24.11 runtime is older than jsdom 31's declared Node 24.15 minimum; the tests and build nevertheless completed successfully. Configure the Jenkins `node-22` tool to use Node 22.22.2 or newer within the 22.x line, or Node 24.15 or newer, and keep `package.json`'s engine range aligned with the lockfile dependencies.

## Reconciliation behavior

Order-service persists per-item settlement state and retries commits for persisted orders and releases for cancelled orders. Product-service reaps reservations still in `RESERVED` after the configured one-hour lease and uses conditional state transitions so stock can only be restored once. This recovers abandoned pre-persistence reservations and retries persisted-order settlement while order-service is available within the lease. Because the services have independent databases, this is a saga with compensation, not a distributed ACID transaction. Address and payment provider fields are not part of the current checkout API; no payment gateway or simulated charge is claimed.