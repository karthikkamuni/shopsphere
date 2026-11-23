# ShopSphere – Architecture

## 1. Runtime architecture (inside the EKS cluster)

```
                          (nothing below is exposed to the internet)

 Namespace: shopsphere
 ┌──────────────────────────────────────────────────────────────────────────┐
 │                                                                          │
 │   Service: order-service :8083  ──►  Deployment order-service  (2 pods)  │
 │         │      │                          │                              │
 │         │      │ HTTP (cluster DNS)       │ JDBC                         │
 │         │      ▼                          │                              │
 │         │  Service: user-service :8081 ──►  Deployment user-service (2)  │
 │         │      │                          │ JDBC                         │
 │         ▼      │                          │                              │
 │  Service: product-service :8082 ──► Deployment product-service (2 pods)  │
 │                                           │ JDBC                         │
 │                                           ▼                              │
 │                    Headless Service: mysql :3306                         │
 │                                  │                                       │
 │                       StatefulSet mysql (1 pod) ──► PVC ──► EBS gp3      │
 │                       databases: userdb | productdb | orderdb            │
 └──────────────────────────────────────────────────────────────────────────┘
```

| Component | Kind | Port | Database | Calls |
|-----------|------|------|----------|-------|
| user-service | Deployment + ClusterIP Service | 8081 | `userdb` | – |
| product-service | Deployment + ClusterIP Service | 8082 | `productdb` | – |
| order-service | Deployment + ClusterIP Service | 8083 | `orderdb` | `http://user-service:8081`, `http://product-service:8082` |
| mysql | StatefulSet + headless Service | 3306 | holds all three databases | – |

**Database per service.** Each service owns one database and never reads another
service's tables. `order-service` stores `userId` / `productId` as plain numbers
and asks the other services over HTTP when it needs to check them.

**Internal communication.** Kubernetes runs a DNS server (CoreDNS). A Service named
`user-service` in namespace `shopsphere` is resolvable as `user-service` (from the same
namespace) or `user-service.shopsphere.svc.cluster.local` (from anywhere in the cluster).
`order-service` reads the addresses from the environment variables
`SERVICES_USER_URL` and `SERVICES_PRODUCT_URL`, so the same jar works locally
(`http://localhost:8081`) and in the cluster (`http://user-service:8081`).

**Why ClusterIP only.** A ClusterIP Service has no public address, so none of the three
services is reachable from the internet. You reach them with `kubectl port-forward` or from
another pod. A real deployment would add one public entry point (an ALB Ingress or API
gateway) in front of them; that is deliberately out of scope here.

## 2. CI/CD architecture

```
 Developer                GitHub repository                         Jenkins
 ─────────                ─────────────────                         ───────
 git push ───────────────► branch user-service    ─┐
                           branch product-service  ├─ branch indexing ─► Multibranch job
                           branch order-service   ─┘   (scan / webhook)   (one per service)
                           branch main (docs only)                              │
                                                                                ▼
                                                                    Jenkins agent (Linux)
                                                                    ┌─────────────────────────┐
                                                                    │ 1 Checkout              │
                                                                    │ 2 Environment validation│
                                                                    │ 3 Maven build  ─► JAR   │
                                                                    │ 4 Unit tests            │
                                                                    │ 5 Docker image build    │
                                                                    │ 6 AWS authentication    │
                                                                    │ 7 Push image to ECR ──────────► Amazon ECR
                                                                    │ 8 Deploy to EKS ──────────┐     shopsphere-<svc>:<sha>-b<n>
                                                                    │ 9 Verify deployment       │
                                                                    └───────────────────────────┼──┘
                                                                                                ▼
                                                                    Amazon EKS  (kubectl apply -n shopsphere)
                                                                    nodes pull the image from ECR
```

* **One branch = one service = one pipeline.** A push to `user-service` can only build and
  deploy `user-service`.
* **Image tag** = `<8-char git commit sha>-b<jenkins build number>`, for example
  `1a2b3c4d-b7`. The commit tells you *what code*, the build number keeps tags unique if the same
  commit is built twice. ECR repositories are created with **immutable tags**, so a tag can never be
  silently replaced.
* **Deployment = apply a manifest with a new image.** The pipeline renders
  `k8s/deployment.yaml` (replacing `__IMAGE_URI__`) and runs `kubectl apply`. Kubernetes performs
  a rolling update: new pods start, pass their readiness probe, and only then are old pods stopped
  (`maxUnavailable: 0`).
* **Failure handling.** If `kubectl rollout status` does not succeed within 5 minutes, the pipeline
  prints diagnostics, runs `kubectl rollout undo` and fails the build.

## 3. Naming conventions (all must match)

| Thing | user | product | order |
|-------|------|---------|-------|
| Git branch | `user-service` | `product-service` | `order-service` |
| Directory in branch | `user-service/` | `product-service/` | `order-service/` |
| Maven artifactId / jar | `user-service` / `target/user-service.jar` | `product-service` | `order-service` |
| Docker local tag | `shopsphere-user-service:<tag>` | `shopsphere-product-service:<tag>` | `shopsphere-order-service:<tag>` |
| ECR repository | `shopsphere-user-service` | `shopsphere-product-service` | `shopsphere-order-service` |
| Deployment / Service | `user-service` | `product-service` | `order-service` |
| Port | 8081 | 8082 | 8083 |
| Database | `userdb` | `productdb` | `orderdb` |
| Multibranch Script Path | `user-service/Jenkinsfile` | `product-service/Jenkinsfile` | `order-service/Jenkinsfile` |

`scripts/check_consistency.py` verifies this table automatically against the Git branches.

## 4. What is intentionally simplified

* One MySQL pod on one EBS volume (no replication or backups). Production would use Amazon RDS.
* `spring.jpa.hibernate.ddl-auto=update` creates tables automatically. Production uses Flyway or Liquibase.
* No public ingress, TLS, autoscaling, service mesh, Helm, Terraform or GitOps – by design.
* Credentials for Jenkins are an IAM user access key (easy to understand). In production prefer an
  IAM role on the agent or OIDC federation, which avoids long-lived keys.

## 5. Reference repository (what was studied and what was deliberately not copied)

The reference, `devops0014/microservices-project`, was cloned and read on 2026-11-22. What was seen:

* It has **one branch per service** (adservice, cartservice, checkoutservice, currencyservice, emailservice,
  frontend, loadgenerator, paymentservice, productcatalogservice, recommendationservice, shippingservice) plus
  `main` and `Infra-Steps`. The services are Google's "Online Boutique" demo (gRPC, several languages).
* Each service branch has its own `Jenkinsfile` that builds an image and pushes it to Docker Hub as `:latest`
  (the adservice branch has two stages: build and push; nothing deploys from it).
* `main` holds a `Jenkinsfile` plus `deployment-service.yml`; the pipeline applies that one manifest file to an
  EKS cluster with `kubectl` using a stored Kubernetes token, with the cluster URL, cluster name, namespace and
  credential IDs written into the Jenkinsfile.

What ShopSphere **keeps** from it: the branch-per-service layout and one pipeline per branch.

What ShopSphere does **differently**: the three services are Java/Spring Boot and independent (own database);
each branch's pipeline both builds *and* deploys only its own service; images go to ECR with unique
`<sha>-b<build>` tags instead of `latest`; no cluster URL, account ID, region or secret is written into any
file (they come from Jenkins configuration and credentials); and every Deployment has probes, resource limits and
a non-root security context.
