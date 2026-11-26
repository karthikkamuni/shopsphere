# ShopSphere – Architecture

## 1. Runtime architecture (inside the EKS cluster)

```
   Browser
      │  http://<load-balancer-address>        (the ONLY public entry point)
      ▼
 Namespace: shopsphere
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  Service: frontend :80 (LoadBalancer) ──► Deployment frontend (2 pods)   │
 │    nginx :8080 serves the React app and forwards only /api/users,        │
 │    /api/products and /api/orders to the three Services below             │
 │         │ (cluster DNS)                                                  │
 │         ▼                                                                │
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
| frontend | Deployment + **LoadBalancer** Service (80 → 8080) | 80 / 8080 | – | `user-service:8081`, `product-service:8082`, `order-service:8083` (through nginx) |
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

**Why ClusterIP for the backends.** A ClusterIP Service has no public address, so none of the three
Java services is reachable from the internet. You reach them with `kubectl port-forward` or from
another pod. The **frontend** is the single public entry point: its Service is `type: LoadBalancer`
(AWS creates a load balancer and gives it a DNS name). The browser never talks to a backend
directly; it calls relative URLs such as `/api/users`, and the nginx inside the frontend pod forwards
only `/api/users`, `/api/products` and `/api/orders` to the matching Service. Actuator endpoints are
not forwarded, so they stay private. A production system would put an ALB Ingress, TLS and
authentication in front instead; that is deliberately out of scope here.

**Why a proxy and not CORS.** Because the page and the API share one origin (the load balancer's
address), no CORS configuration is needed in the Java services, and no backend address is baked into
the JavaScript bundle. The same built image therefore works in every environment. In local
development the Vite dev server plays the role of nginx (see `vite.config.js`).

## 2. CI/CD architecture

```
 Developer                GitHub repository                         Jenkins
 ─────────                ─────────────────                         ───────
 git push ───────────────► branch user-service    ─┐
                           branch product-service  ├─ branch indexing ─► Multibranch job
                           branch order-service    │   (scan / webhook)   (one per service)
                           branch frontend        ─┘
                           branch main (docs only)                              │
                                                                                ▼
                                                                    Jenkins agent (Linux)
                                                                    ┌─────────────────────────┐
                                                                    │ 1 Checkout              │
                                                                    │ 2 Environment validation│
                                                                    │ 3 Maven build  ─► JAR   │   (frontend: npm ci +
                                                                    │                         │    npm run build ─► dist/)
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
  deploy `user-service`. The `frontend` branch follows the same nine stages; only stages 3 and 4 use
  Node.js (`npm ci`, `npm run build`, `vitest`) instead of Maven, and the Jenkins agent needs the
  NodeJS plugin with a tool named `node-22`.
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

| Thing | user | product | order | frontend |
|-------|------|---------|-------|----------|
| Git branch | `user-service` | `product-service` | `order-service` | `frontend` |
| Directory in branch | `user-service/` | `product-service/` | `order-service/` | `frontend/` |
| Build output | `target/user-service.jar` | `target/product-service.jar` | `target/order-service.jar` | `dist/` (static files) |
| Docker local tag | `shopsphere-user-service:<tag>` | `shopsphere-product-service:<tag>` | `shopsphere-order-service:<tag>` | `shopsphere-frontend:<tag>` |
| ECR repository | `shopsphere-user-service` | `shopsphere-product-service` | `shopsphere-order-service` | `shopsphere-frontend` |
| Deployment / Service | `user-service` | `product-service` | `order-service` | `frontend` |
| Port | 8081 | 8082 | 8083 | 8080 in the pod, 80 on the Service |
| Database | `userdb` | `productdb` | `orderdb` | – |
| Multibranch Script Path | `user-service/Jenkinsfile` | `product-service/Jenkinsfile` | `order-service/Jenkinsfile` | `frontend/Jenkinsfile` |

The Service names `user-service`, `product-service` and `order-service` are also hard-wired into the
frontend's `nginx.conf` (nginx proxies to `http://user-service:8081` and so on).

`scripts/check_consistency.py` verifies this table automatically against the Git branches.

## 4. What is intentionally simplified

* One MySQL pod on one EBS volume (no replication or backups). Production would use Amazon RDS.
* `spring.jpa.hibernate.ddl-auto=update` creates tables automatically. Production uses Flyway or Liquibase.
* **The UI has no login and its load balancer is open to the internet** (plain HTTP, no TLS). Anyone with
  the address can create users, products and orders. Use it for practice, restrict it with
  `loadBalancerSourceRanges` in `frontend/k8s/service.yaml` or use `kubectl port-forward`, and delete it
  when done. No Ingress, TLS, autoscaling, service mesh, Helm, Terraform or GitOps – by design.
* nginx resolves the backend Service names **once, at start-up**. If a Service does not exist yet, the
  frontend pod crash-loops with `host not found in upstream`, so deploy the frontend **last**, and
  restart it (`kubectl rollout restart deployment/frontend`) if you ever delete and recreate a backend Service.
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
