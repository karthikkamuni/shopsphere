# Validation report

This file says exactly what was checked, with which tool, and what was **not** checked. Nothing here is
an invented test result: the checks listed as "done" were run on the committed files of all five branches.

## A. Checks that were run

| # | Check | Tool | Result |
|---|-------|------|--------|
| 1 | Dockerfile lint (4 files: 3 services + frontend) | hadolint 2.15.1 | Clean. (It first flagged `HEALTHCHECK … CMD` shell form, rule DL3025; changed to JSON form.) |
| 2 | Kubernetes schema, strict mode (all 13 distinct manifests: 4 Deployments, 4 Services, Namespace, StorageClass, MySQL StatefulSet/Service/ConfigMap) | kubeconform 0.8.0 | 13 valid, 0 invalid. Deployments were checked both with the `__IMAGE_URI__` placeholder and rendered with a sample image. Validated against the tool's default Kubernetes schema version, not necessarily your cluster's. |
| 3 | Jenkinsfile Groovy syntax (4 files) | npm-groovy-lint | 0 errors. 8 warnings per file, all `GStringExpressionWithinString`: shell snippets inside single-quoted Groovy strings, where `${VAR}` is intentionally expanded by the **shell**, not by Groovy. |
| 4 | The three backend Jenkinsfiles are identical except for service names | `diff` after name substitution | user = product = order. (The frontend Jenkinsfile differs on purpose: Node.js build/test instead of Maven.) |
| 5 | `pom.xml` files well-formed (3) | Python XML parser | OK (this does **not** resolve dependencies) |
| 6 | Shell scripts parse | `bash -n` | OK |
| 7 | JSON policies parse | Python `json` | OK |
| 8 | `docker-compose.yml` | `docker compose config` | Valid with the two password variables set; refuses to render when they are missing (as designed) |
| 9 | README internal links | script | 23 anchors, 0 broken; linked files exist |
| 10 | **Cross-branch consistency** (`scripts/check_consistency.py`) | Python + `git show` | **225 checks, 225 passed.** (While adding the frontend, one check failed because of a bug in the checker itself: a comment in `nginx.conf` containing the word "actuator" was matched. The checker was fixed to ignore comments, and a mutated config that really proxies `/actuator` is still detected.) |
| 11 | Secret / account-id / region scan of all five branches | `git grep` | No AWS keys, no 12-digit account IDs and no region names found (the only account-like number is the dummy `123456789012` in the README) |
| 12 | **Frontend unit tests** (7 Vitest + Testing Library tests: loads data from all three services; adds a user; explains a duplicate e-mail; adds a product; previews the total and places an order; blocks an order without a user and product; shows an unreachable service and recovers on retry) | `npm ci && npm test` in a **fresh clone of the `frontend` branch** | 7 passed, 0 failed |
| 13 | **Frontend production build** | `npm run build` (Vite 8) in the same fresh clone | Succeeds; `dist/index.html` + hashed JS/CSS/fonts produced (≈72 kB gzip JS) |
| 14 | **Frontend end-to-end in a real browser** | Playwright (Chromium) against small mock versions of the three APIs, once through the Vite preview server and once through a real **nginx 1.24** running the committed `nginx.conf` with the real `dist/` | Create two users (a duplicate e-mail shows the error message), create products, preview and place orders, orders listed; no horizontal scroll at 390 px width; keyboard focus outline visible; no browser console errors or warnings. |
| 15 | **nginx behaviour**, checked with `curl` against nginx 1.24 and the committed `nginx.conf` (re-run on the final committed files) | nginx 1.24 + `curl` | `/healthz` → 200; `/` and unknown page paths such as `/some/spa/route` → `index.html` (single-page fallback); `/api/users|products|orders` reach the services; unknown `/api/...` → JSON 404; `/actuator/health` is **not** proxied (returns the web page, never a backend response); the three security headers appear on `/`, on `/assets/*` and on proxied `/api/*` responses; `/assets/*` cached for a year, `/` not cached; gzip on. With a backend name that does not resolve, nginx refuses to start (`host not found in upstream`), which is why README tells you to deploy the frontend last. |
| 16 | Frontend `junit.xml` produced for Jenkins | `npm test -- --reporter=junit …` exactly as in the Jenkinsfile | File written with 7 test cases |

The consistency script verifies, **reading straight from the Git branches**:

* each service branch contains only its own directory, with `pom.xml`, `Dockerfile`, `Jenkinsfile`, `k8s/deployment.yaml`, `k8s/service.yaml`, sources and tests, and no committed `target/`
* the Jenkinsfile of each branch sets its own `SERVICE_NAME`, `ECR_REPOSITORY` (`shopsphere-<service>`) and namespace, has the nine required stages in order, uses the credentials binding, builds the tag from the commit SHA, restricts deploy stages to its own branch, and mentions no other service
* `pom.xml` artifactId / Java 21 / Spring Boot 3.5.x / jar name ↔ Dockerfile `COPY` ↔ `.dockerignore`
* port numbers agree across `application.properties`, Dockerfile (`EXPOSE`, `SERVER_PORT`), Deployment (`containerPort`, env) and Service
* Deployment / Service names, labels and selectors, probes' paths, resource requests and limits, security context
* JDBC URL host `mysql` and database name per service ↔ the databases the MySQL init script creates ↔ Service name `mysql`
* Secret name and keys used by the services ↔ the keys MySQL reads; StatefulSet storage class ↔ the StorageClass that exists
* order-service's `SERVICES_USER_URL` / `SERVICES_PRODUCT_URL` ↔ the other services' names and ports
* the init script in the ConfigMap is byte-identical to `local/init-databases.sh`
* the `frontend` branch: only `frontend/`, no committed `node_modules`/`dist`; Jenkinsfile identity, nine stages, NodeJS tool, deploy guard, no backend names; `package.json` ↔ `package-lock.json` dependency sets agree (so `npm ci` accepts the lock file); Dockerfile / `.dockerignore` / nginx port 8080 / user 101; nginx proxy targets ↔ the backend Service names and ports; nginx does not proxy actuator and uses `add_header` only at server level; Deployment and `LoadBalancer` Service fields
* README mentions all four ECR repositories and all four Script Paths; `create-ecr-repos.sh` loops over all four services

**The checker itself was tested.** On a throwaway clone, three deliberate mistakes were injected (product
pipeline pointing at `user-service`, order-service `containerPort` changed, wrong namespace in a Service). It
reported 4 failing checks covering all three, so a green result is meaningful. (That test was run before the frontend checks were added; for the frontend, the actuator check was mutation-tested as described in row 10.)

## B. What could NOT be validated here – do these on your own machines

The sandbox used to build this repository has **no Maven, no reachable Maven Central, no Docker daemon, no
Docker Hub access, no Jenkins and no AWS account**. Therefore:

| Not verified | Why it matters | How to verify |
|--------------|----------------|---------------|
| **Maven build and unit tests** – `mvn package` / `mvn test` were never run; the Java code was **not compiled**. (A parse-only `javac` pass was run but can only report unresolved imports without Spring on the classpath, so it proves nothing.) There are 20 tests: 6 user, 6 product, 8 order. | The code was written carefully against Spring Boot 3.5 / Spring Framework 6.2 APIs, but a compile error or a failing test is still possible. Also, `spring-boot-starter-parent:3.5.16` is expected to be on Maven Central but this could not be confirmed from here. | `cd <service> && mvn -B clean package` on each branch. **Do this first.** If a test fails, fix it on that branch before wiring up Jenkins. |
| **Docker build and run** (all four images) | The base image tags `eclipse-temurin:21-jre-alpine` and `nginxinc/nginx-unprivileged:stable-alpine`, the Alpine `addgroup`/`adduser` flags, the JVM starting under a read-only root filesystem, nginx running as UID 101 with a read-only root filesystem, and Docker's handling of the frontend `.dockerignore` (`*` then `!dist`, `!nginx.conf`) were not exercised. (nginx 1.24 was tested from the OS package, not from the Docker image.) | Section 7 of the README. Then run the container with MySQL and call `/actuator/health`. |
| **Jenkins execution** | The declarative syntax was only linted as Groovy. Plugin behaviour (`tools {}` including the frontend's `nodejs 'node-22'` tool, `AmazonWebServicesCredentialsBinding`, `when { expression }`, `post`) was never executed. | Ask Jenkins to validate a Jenkinsfile: `curl -u <user>:<api-token> -X POST -F "jenkinsfile=<user-service/Jenkinsfile" "$JENKINS_URL/pipeline-model-converter/validate"`, then run the real job. |
| **AWS** – ECR push, `aws eks update-kubeconfig`, IAM policy sufficiency, EKS access entry and `AmazonEKSEditPolicy` permissions (including the pipeline's `kubectl auth can-i` check), `eksctl` and EBS CSI add-on commands | All commands follow AWS documentation but were never run against an account. | Follow README sections 10–11. If a permission is missing, the pipeline stops in "AWS authentication" with a message naming it. |
| **Kubernetes runtime behaviour** – probes, MySQL first-start init script, EBS volume binding, rolling update, rollback, **provisioning of the frontend's AWS load balancer and its address** | Only the YAML *schema* was validated. | README section 12.4; `kubectl get pods`, `kubectl describe`. |
| **Real Multibranch discovery** | Needs your GitHub repository and Jenkins. | README section 9; read the *Scan Multibranch Pipeline Log*. |
| **UI against the real Java services** | The browser tests above used mock APIs that imitate the documented JSON shapes; the Java services were never compiled or started here. | README section 6: start the three services from STS (or Docker Compose), then `npm ci && npm run dev` in `frontend/`. If a field name differs, the UI shows the error; fix the mismatch in `src/api.js` or the service DTO. |

Known simplifications (not defects): single MySQL pod without backups, `ddl-auto=update`, the frontend's public HTTP load balancer without login or TLS,
IAM user access key instead of an IAM role for Jenkins, Spring Boot 3.5 being at the end of open-source support.
