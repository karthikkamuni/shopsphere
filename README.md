# ShopSphere – Microservices CI/CD on Jenkins, Docker, Amazon ECR and Amazon EKS

A small e-commerce app made of **three independent Spring Boot microservices and a React web UI**, built to practise
one deployment path from start to finish:

```
GitHub  →  Jenkins Multibranch Pipeline  →  Maven / npm  →  Docker  →  Amazon ECR  →  Amazon EKS
```

The business logic is deliberately tiny. The point is the pipeline.

> **This `main` branch holds documentation, Kubernetes base manifests (namespace, MySQL) and helper
> scripts only. The source code lives on four other branches (three services and the web UI).** See [section 4](#4-git-branch-strategy).

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Architecture diagram](#2-architecture-diagram)
3. [Technology stack](#3-technology-stack)
4. [Git branch strategy](#4-git-branch-strategy)
5. [Repository structure](#5-repository-structure)
6. [Local application execution](#6-local-application-execution)
7. [Docker build instructions](#7-docker-build-instructions)
8. [Jenkins setup](#8-jenkins-setup)
9. [Multibranch Pipeline configuration](#9-multibranch-pipeline-configuration)
10. [AWS ECR configuration](#10-aws-ecr-configuration)
11. [EKS cluster setup](#11-eks-cluster-setup)
12. [Kubernetes deployment](#12-kubernetes-deployment)
13. [Troubleshooting](#13-troubleshooting)
14. [Cleanup instructions](#14-cleanup-instructions)

Also read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (naming table, design notes) and
[`docs/VALIDATION.md`](docs/VALIDATION.md) (what was and was **not** verified).

---

## 1. Project overview

| Service | Purpose | Port | Database | API |
|---------|---------|------|----------|-----|
| **user-service** | Registration, JWT authentication, roles, account and profile management | 8081 | `userdb` | `/api/auth/*`, `/api/users/*`, trusted `/internal/users/*` |
| **product-service** | Public catalogue, ADMIN management, inventory reservations | 8082 | `productdb` | `/api/products/*`, trusted `/internal/products/*` |
| **order-service** | Persistent carts, checkout saga, order history and administration | 8083 | `orderdb` | `/api/cart/*`, `/api/orders/*`, `/api/admin/*` |
| **frontend** | Customer storefront and admin dashboard; the only public entry point | 8080 in the container, 80 on the Service | – | nginx routes auth/users, products, cart, orders and admin APIs to their owning services |

Every Spring Boot service also exposes `GET /actuator/health` (plus `/actuator/health/liveness` and
`/actuator/health/readiness`, used by Kubernetes probes).

What you will practise:

* Jenkins **Multibranch Pipeline** branch discovery (one branch per service, plus one for the web UI)
* A declarative Jenkinsfile with nine stages, credentials binding and unique image tags
* Building a JAR with Maven (services) or a static site with npm (UI) and packaging it with Docker
* Pushing images to **Amazon ECR**
* Rolling updates on **Amazon EKS** with `kubectl`, probes, resource limits and rollback
* Service-to-service communication inside a cluster, and MySQL persistence on an EBS volume
* Exposing exactly one public entry point (the UI behind a load balancer) while everything else stays private

Deliberately **not** used: Terraform, Helm, Argo CD, service mesh, GitOps.

---

## 2. Architecture diagram

Runtime (inside EKS, namespace `shopsphere`). **Only the frontend is public.**

```
 Internet ─► AWS load balancer ─► Service frontend :80 ─► nginx pods (2)
                                                               │  serves the React site and forwards
                                                               │  /api/auth, /api/users, /api/products,
                                                               │  /api/cart, /api/orders, /api/admin
 ┌─────────────────────────── namespace: shopsphere ───────────┼───────────────────────┐
 │        ┌─────────────────────────────────┬──────────────────┘                       │
 │        ▼                                 ▼                       ▼                    │
 │  user-service :8081              product-service :8082    order-service :8083         │
 │  (2 pods, ClusterIP)             (2 pods, ClusterIP)      (2 pods, ClusterIP)         │
 │        ▲                                 ▲                       │                    │
 │        └───────── HTTP (cluster DNS) ────┴───────────────────────┘  order-service     │
 │        │ JDBC                            │ JDBC                  │ JDBC   calls both   │
 │        └───────────────┬─────────────────┴───────────────────────┘                    │
 │                        ▼                                                              │
 │               Service mysql :3306  (databases userdb, productdb, orderdb)             │
 │               StatefulSet mysql ──► PVC ──► EBS gp3 volume                            │
 └───────────────────────────────────────────────────────────────────────────────────────┘
```

CI/CD:

```
 git push ─► GitHub ─► Jenkins Multibranch job ─► agent: checkout → validate → mvn package → mvn test
 (branch                (one job per service)            → docker build → ECR login → docker push
  user-service)                                          → kubectl apply → kubectl rollout status
                                                                  │                    │
                                                         Amazon ECR image      Amazon EKS rolling update
                                                    shopsphere-user-service:<sha>-b<n>
```

The `frontend` branch follows the same flow, with `npm ci`, `npm run build` and `npm test` instead of Maven.

A larger diagram and a naming table are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

**How the services talk to each other.** Each service has a `ClusterIP` Service. Kubernetes DNS turns
the name `user-service` into that Service's internal IP, so `order-service` simply calls
`http://user-service:8081`. The address is not written in Java code: it comes from the environment
variable `SERVICES_USER_URL` (and `SERVICES_PRODUCT_URL`). Locally those default to `localhost`.
The three backend Services are `ClusterIP`, so they have no public address. The one public entry point
is the **frontend**: its Service is a `LoadBalancer`, and nginx routes `/api/auth` and `/api/users` to
user-service, `/api/products` to product-service, and `/api/cart`, `/api/orders`, and `/api/admin` to
order-service. Actuator endpoints remain private. The browser calls relative URLs, so no CORS configuration is needed. A larger real system would use an
The app has authenticated USER and ADMIN workflows; frontend guards only improve navigation. For public deployments, add HTTPS and restrict access to trusted addresses.

---

## 3. Technology stack

| Area | Choice |
|------|--------|
| Language / runtime | Java 21 (Eclipse Temurin) |
| Framework | Spring Boot **3.5.16** – Web, Data JPA, Validation, Actuator |
| Build | Maven (Spring Boot parent POM, `finalName` = artifactId → `target/<service>.jar`) |
| Database | MySQL 8.4 (H2 in-memory for unit tests only) |
| Container | Docker, base image `eclipse-temurin:21-jre-alpine`, non-root UID 10001 |
| Frontend | React 19, Vite 8, Tailwind CSS 4, Vitest + Testing Library (Node 22.22.2 or newer) |
| Web server | nginx (`nginxinc/nginx-unprivileged`, non-root UID 101): serves the built site and proxies `/api` |
| CI | Jenkins, Multibranch Pipeline, declarative Jenkinsfile |
| Registry | Amazon ECR (one repository per branch, immutable tags) |
| Orchestration | Amazon EKS (Kubernetes), `kubectl` |

> **Spring Boot 3.5 support note.** 3.5.16 (June 2026) was announced as the **last open-source release
> of the 3.5.x line**. That is fine for a practice project. For anything long-lived, move to a
> currently supported Spring Boot line.

---

## 4. Git branch strategy

One repository, **five branches with different content** (the service branches are *orphan* branches –
they share no history with `main`, so each contains only its own files):

| Branch | Contains | Jenkins job builds it? |
|--------|----------|------------------------|
| `main` | README, docs, MySQL + namespace manifests, scripts. No service code, no Jenkinsfile. | No |
| `user-service` | only the `user-service/` directory | Yes – job for user-service |
| `product-service` | only the `product-service/` directory | Yes – job for product-service |
| `order-service` | only the `order-service/` directory | Yes – job for order-service |
| `frontend` | only the `frontend/` directory (React web UI) | Yes – job for frontend |

Layout of every service branch (example: `user-service`):

```
user-service/
  pom.xml
  Dockerfile
  .dockerignore
  Jenkinsfile
  src/main/java/...      src/main/resources/application.properties
  src/test/java/...      src/test/resources/application-test.properties
  k8s/
    deployment.yaml
    service.yaml
```

Layout of the `frontend` branch:

```
frontend/
  package.json, package-lock.json   dependency versions, pinned by the lock file
  vite.config.js, index.html, public/
  src/                              React app (components, api.js) and tests (App.test.jsx)
  nginx.conf                        serves the site and proxies /api to the three services
  Dockerfile, .dockerignore, Jenkinsfile
  k8s/
    deployment.yaml
    service.yaml                    type LoadBalancer: the only public Service
```

Why this is good for learning: pushing a commit to `product-service` triggers **only** the product
pipeline, and that pipeline can only deploy `product-service`.

### Create the branches and push them (exact commands)

If you received this repository already prepared (all five branches exist locally), you only need
**step 3**.

```bash
# 1. Create an EMPTY repository on GitHub first (no README, no .gitignore), e.g. shopsphere.

# 2. (Only if rebuilding branches from scratch.) Pattern for one service branch:
git checkout --orphan user-service         # new branch with no history
git rm -rf .                               # empty the working tree
# ...copy the user-service/ directory into the repository root...
git add user-service
git commit -m "user-service: initial Spring Boot service, Dockerfile, Jenkinsfile, k8s manifests"
# repeat for product-service, order-service and frontend; main was created first with the docs

# 3. Connect to GitHub and push all five branches
git remote add origin https://github.com/<your-github-username>/shopsphere.git
git push -u origin main user-service product-service order-service frontend

# 4. Check
git branch -a
git ls-remote --heads origin
```

The commits in this repository carry a placeholder author name. To record them as **you** before the
first push (each branch has exactly one commit, so this is safe), set your identity and re-author them:

```bash
git config user.name  "Your Name"
git config user.email "you@example.com"
for b in main user-service product-service order-service frontend; do
  git checkout "$b" && git commit --amend --reset-author --no-edit
done
git checkout main
```

Day-to-day work on one service:

```bash
git checkout product-service          # the working tree now contains only product-service/
# ...edit code...
git add -A && git commit -m "product-service: add price validation"
git push                              # Jenkins detects the push and builds this branch only
```

Switching branches swaps the directory contents completely – that is expected with this layout.
To see two services side by side use worktrees:
`git worktree add ../shopsphere-user user-service`.

---

## 5. Repository structure

What `main` contains:

```
README.md
docs/
  ARCHITECTURE.md             diagrams, naming table, design notes
  VALIDATION.md               what was verified, and what needs a real AWS/Jenkins environment
k8s/
  00-namespace.yaml           namespace "shopsphere"
  01-storageclass-gp3.yaml    EBS gp3 StorageClass for MySQL
  mysql/
    mysql-init-configmap.yaml creates userdb / productdb / orderdb on first start
    mysql-service.yaml        headless Service "mysql"
    mysql-statefulset.yaml    MySQL 8.4 + 5 GiB persistent volume
aws/
  jenkins-iam-policy.json     least-privilege IAM policy for the Jenkins user
  ecr-lifecycle-policy.json   keep the 10 newest images per repository
scripts/
  create-ecr-repos.sh         creates the four ECR repositories
  check_consistency.py        cross-branch consistency checker
local/
  docker-compose.yml          MySQL for local runs
  init-databases.sh           same init script, used by compose
```

Service branches: see [section 4](#4-git-branch-strategy).

---

## 6. Local application execution

Prerequisites: Git, Docker Engine with Compose, Java 21 and Maven 3.9+ (for building the backend images), Node 22.22.2+ (for building the frontend image).

The four applications live on separate branches. Create local worktrees and build the images from each branch's service directory:

```bash
git worktree add ../shopsphere-user user-service
git worktree add ../shopsphere-product product-service
git worktree add ../shopsphere-order order-service
git worktree add ../shopsphere-frontend frontend

docker build -t shopsphere-user-service:local ../shopsphere-user/user-service
docker build -t shopsphere-product-service:local ../shopsphere-product/product-service
docker build -t shopsphere-order-service:local ../shopsphere-order/order-service
(cd ../shopsphere-frontend/frontend && npm ci && npm run build)
docker build -t shopsphere-frontend:local ../shopsphere-frontend/frontend
```

Set local-only credentials in the current shell. Generate different random values for the JWT signing secret and service-to-service key; never put real values in Git or image build arguments:

```bash
export MYSQL_ROOT_PASSWORD='local-only-root-password'
export MYSQL_PASSWORD='local-only-app-password'
export JWT_SECRET="$(openssl rand -hex 32)"
export SERVICE_API_KEY="$(openssl rand -hex 32)"
export SHOPSPHERE_ADMIN_NAME='ShopSphere Administrator'
export SHOPSPHERE_ADMIN_EMAIL='admin@example.test'
export SHOPSPHERE_ADMIN_PASSWORD='use-a-local-password-of-at-least-10-characters'

docker compose -f local/docker-compose.yml up -d
```

Compose waits for MySQL and backend readiness, then starts the frontend on <http://localhost:8080>. The initial ADMIN is created only if its configured email is absent; product-service seeds its catalog once. MySQL data persists in the `mysql-data` volume across container restarts. `docker compose -f local/docker-compose.yml down` keeps that data; adding `-v` deletes it.

The checkout contract currently records the authenticated account, cart, immutable product/price snapshots and order status. There is no shipping-address or payment-provider contract, so this app does not collect addresses or simulate a real charge. Payment integration should be added only with an approved provider and an order/payment contract.

To run the Vite development server instead of the frontend container, start the three backends with the same shared `JWT_SECRET` and `SERVICE_API_KEY`, then run `npm ci && npm run dev` from `../shopsphere-frontend/frontend`. Vite proxies the documented API prefixes to ports 8081–8083.
## 7. Docker build instructions

Workflow (the same one Jenkins automates):

1. **Maven builds the application** → `target/<service>.jar`
2. **Docker builds the image** from the `Dockerfile`, which copies that JAR
3. **The image is tagged** `shopsphere-<service>:<tag>`

```bash
cd user-service                                   # on branch user-service
mvn -B clean package -DskipTests                  # creates target/user-service.jar
docker build -t shopsphere-user-service:local .
docker run --rm -p 8081:8081 \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://host.docker.internal:3306/userdb?useSSL=false&allowPublicKeyRetrieval=true' \
  -e SPRING_DATASOURCE_USERNAME=shopsphere \
  -e SPRING_DATASOURCE_PASSWORD="$MYSQL_PASSWORD" \
  shopsphere-user-service:local
```

### What the Dockerfile does and why

| Line | Reason |
|------|--------|
| `FROM eclipse-temurin:21-jre-alpine` | Java 21 **runtime only** (no compiler) on Alpine → small image |
| `addgroup/adduser … 10001` + `USER 10001:10001` | Runs as a non-root user. A *numeric* ID lets Kubernetes enforce `runAsNonRoot` |
| `COPY --chown=… target/user-service.jar /app/app.jar` | Packages the JAR that Maven produced |
| `ENV SERVER_PORT=8081 JAVA_OPTS=…` | Environment-based configuration; `MaxRAMPercentage` makes the JVM respect container memory limits |
| `EXPOSE 8081` | Documents the port (Kubernetes uses `containerPort`) |
| `HEALTHCHECK` | Lets plain Docker report health; Kubernetes uses its own probes instead |
| `ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]` | `exec` makes Java PID 1, so it receives `SIGTERM` and shuts down gracefully |

### Docker build context – and how Jenkins gets the JAR

* The **build context** is the directory you pass to `docker build` (the final `.`). Docker sends that
  directory to the Docker daemon, and `COPY` can only read files inside it.
* In the pipeline, `dir('user-service') { sh 'mvn … package' }` writes `target/user-service.jar` into
  the **Jenkins workspace on the agent**, e.g. `<agent-root>/workspace/<job>/<branch>/user-service/target/`.
* The next stage runs `docker build .` from that **same directory on the same agent**. The agent's
  Docker daemon is on the same machine, so the JAR is simply a file on disk – nothing is copied between
  machines. This is why the whole pipeline uses one `agent` and why Maven and Docker must be on the same node.
* `.dockerignore` contains `*` and `!target/<service>.jar`, so only the JAR is sent to the daemon
  (a context of a few MB instead of the whole project).
* If the JAR does not exist, the build fails with `COPY failed: file not found` – the pipeline checks
  for it first and prints a clearer message.

### The frontend image

Same idea, different build tool: **npm builds the site on the Jenkins agent first** (`dist/`), and the
Dockerfile only packages the result.

```bash
cd frontend                         # on branch frontend
npm ci && npm run build             # creates dist/
docker build -t shopsphere-frontend:local .

# nginx looks up the service names when it starts, so it needs names to find. Here they point at the
# services running on your own machine (for example from STS). Needs Docker 20.10 or newer.
docker run --rm -p 8080:8080 \
  --add-host=user-service:host-gateway \
  --add-host=product-service:host-gateway \
  --add-host=order-service:host-gateway \
  shopsphere-frontend:local
# open http://localhost:8080
```

| Line | Reason |
|------|--------|
| `FROM nginxinc/nginx-unprivileged:stable-alpine` | Official nginx variant that runs as a non-root user (UID 101), keeps its pid and temp files in `/tmp` and listens on 8080. Its default entrypoint starts nginx, so no `CMD` is needed |
| `COPY nginx.conf /etc/nginx/conf.d/default.conf` | Serves the site and proxies authentication/users, products, cart/orders and admin APIs to their owning services |
| `COPY dist/ /usr/share/nginx/html/` | The built site |
| `USER 101`, `EXPOSE 8080` | Non-root; the Kubernetes Service maps port 80 to 8080 |
| `HEALTHCHECK … /healthz` | nginx answers `/healthz` itself, so the check does not depend on the backends |

The build context is the `frontend/` directory and `.dockerignore` lets only `dist/` and `nginx.conf` in:
`node_modules` and the source never enter the image. As with the JAR, Jenkins does not copy anything: `npm run build`
writes `dist/` into the workspace and `docker build .` reads it from the same directory on the same agent.

---

## 8. Jenkins setup

### 8.1 Tools: controller vs agent

| Item | Controller | Build agent | Notes |
|------|:---:|:---:|-------|
| Java to run Jenkins / the agent process | ✔ | ✔ | Whatever your Jenkins version requires |
| Jenkins plugins | ✔ | – | Plugins are installed on the controller only |
| Credentials store (AWS key, GitHub token) | ✔ | – | Secrets are sent to the agent only while a build step uses them |
| Git | optional | **✔** | Used for `checkout scm` |
| JDK **21** for building | – | **✔** | Auto-installed per build via the `jdk21` tool (see 8.3) or preinstalled |
| Maven 3.9+ | – | **✔** | Auto-installed via the `maven-3` tool or preinstalled |
| Node.js 22.22.2+ (frontend job only) | – | **✔** | Auto-installed via the NodeJS plugin tool `node-22`, or preinstalled |
| Docker Engine (daemon + CLI) | – | **✔** | The build agent runs `docker build/push` |
| AWS CLI v2 | – | **✔** | `aws ecr …`, `aws eks update-kubeconfig` |
| `kubectl` | – | **✔** | Within one minor version of the cluster |
| Network access to ECR, EKS API, Maven Central, GitHub | – | **✔** | |

Recommended: set the controller's executors to **0** (Manage Jenkins → Nodes → Built-In Node) so all
builds run on an agent and the controller never needs Docker, AWS CLI or `kubectl`. If you run a
single all-in-one Jenkins machine, that one machine must have everything in the "agent" column.

### 8.2 Install the tools on the agent (Ubuntu/Debian example)

```bash
sudo apt-get update && sudo apt-get install -y git unzip curl ca-certificates

# Docker Engine: follow https://docs.docker.com/engine/install/ for your distribution, then:
sudo systemctl enable --now docker

# AWS CLI v2
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip     # use aarch64 on ARM
unzip -q awscliv2.zip && sudo ./aws/install && aws --version

# kubectl (pick a version within one minor of your EKS cluster; this downloads the latest stable)
curl -fsSLO "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && kubectl version --client
```

JDK 21 and Maven can be installed by hand (`sudo apt-get install -y openjdk-21-jdk maven` – check the
Maven version is 3.9+) **or** installed automatically by Jenkins (8.3).

### 8.3 Docker permission for the agent user (do not assume `jenkins`)

The Linux account that runs the agent process is **not necessarily called `jenkins`** – it may be
`ubuntu`, `ec2-user`, `ci`, `builder`, or anything else. Find the real one first:

```bash
# On the agent machine – shows which account runs the agent process:
ps -eo user:20,args | grep -E '[a]gent.jar|[j]enkins.war'
# Or let a pipeline tell you – the "Environment validation" stage prints:  Agent OS user : <name>
```

Then grant that account access to Docker, restart the agent process, and verify:

```bash
AGENT_USER=<account-from-the-previous-step>
sudo usermod -aG docker "$AGENT_USER"
# Group membership is read when a process starts: restart the agent
# (systemd service, or disconnect/reconnect an SSH agent). A running process keeps the old groups.
sudo -u "$AGENT_USER" -H docker info        # must print server info, not "permission denied"
```

Security note: membership of the `docker` group is effectively root access on that machine. Use a
dedicated, disposable build agent for this project.

### 8.4 Plugins (Manage Jenkins → Plugins)

| Plugin | Needed for |
|--------|-----------|
| Pipeline (`workflow-aggregator`) | Declarative pipelines, Multibranch project type |
| GitHub Branch Source | Branch discovery and webhooks for GitHub repositories |
| Git | `checkout scm` |
| Credentials Binding | `withCredentials` |
| **CloudBees AWS Credentials** (`aws-credentials`) | The "AWS Credentials" credential kind and `AmazonWebServicesCredentialsBinding` used in the Jenkinsfiles |
| Maven Integration | The `maven` tool type used by `tools { maven 'maven-3' }` |
| Eclipse Temurin installer | Optional – lets Jenkins download JDK 21 automatically |
| NodeJS | The `nodejs` tool used by the frontend Jenkinsfile (`tools { nodejs 'node-22' }`) |
| Timestamper | `timestamps()` option |
| JUnit | Test reports (normally installed with the suggested plugins) |

### 8.5 Global Tool Configuration (Manage Jenkins → Tools)

The names must match the Jenkinsfile **exactly**:

* **JDK** → Add JDK → Name `jdk21` → Install automatically → *Install from adoptium.net* → a `jdk-21.x` version.
  (Or untick auto-install and give `JAVA_HOME` of a JDK 21 already on the agent.)
* **Maven** → Add Maven → Name `maven-3` → Install automatically → a 3.9.x version.
* **NodeJS** (only for the frontend job) → Add NodeJS → Name `node-22` → Install automatically → a 22.x version
  that is 22.22.2 or newer.
* **Git** → the default `git` on the agent's PATH is fine.

Auto-installed tools are downloaded **onto the agent** the first time a build needs them, so the agent
needs internet access.

### 8.6 Global environment variables (Manage Jenkins → System → Global properties → Environment variables)

| Name | Value |
|------|-------|
| `AWS_REGION` | the region of your ECR repositories and EKS cluster (e.g. the one you use with `eksctl`) |
| `EKS_CLUSTER_NAME` | the EKS cluster name (e.g. `shopsphere-eks`) |

The account ID is **not** configured anywhere: the pipeline asks AWS for it (`aws sts get-caller-identity`).
The pipeline fails early with a clear message if either variable is missing.

### 8.7 AWS credentials in Jenkins

See [section 10.4](#104-how-jenkins-gets-aws-credentials) – create an IAM user, then add a Jenkins credential
of kind **AWS Credentials** with ID **`aws-shopsphere`**.

### 8.8 GitHub credentials (only if the repository is private)

Manage Jenkins → Credentials → Global → Add → *Username with password* → username = your GitHub user,
password = a **personal access token** (fine-grained: this repository, *Contents: read-only*;
add *Webhooks* / *Metadata* read as GitHub Branch Source requires). ID e.g. `github-shopsphere`.

---

## 9. Multibranch Pipeline configuration

### Why one Multibranch job per branch?

A Multibranch job has **one Script Path for all its branches**. Because each service's Jenkinsfile lives
at a different path (`user-service/Jenkinsfile`, `product-service/Jenkinsfile`,
`order-service/Jenkinsfile`, `frontend/Jenkinsfile`), you create **one Multibranch job per branch** (four jobs). Each job scans the whole
repository, **discovers every branch**, and builds only the branches that contain *its* Jenkinsfile.
Branches without that file are reported as not meeting the criteria and are skipped – that is
branch discovery in action.

| Job name | Script Path | Branch it builds |
|----------|-------------|------------------|
| `shopsphere-user-service` | `user-service/Jenkinsfile` | `user-service` |
| `shopsphere-product-service` | `product-service/Jenkinsfile` | `product-service` |
| `shopsphere-order-service` | `order-service/Jenkinsfile` | `order-service` |
| `shopsphere-frontend` | `frontend/Jenkinsfile` | `frontend` |

### Steps (repeat for each job)

1. Jenkins home → **New Item** → name `shopsphere-user-service` → **Multibranch Pipeline** → OK.
2. **Branch Sources** → *Add source* → **GitHub**.
   * Credentials: your GitHub credential (leave empty for a public repository).
   * Repository HTTPS URL: `https://github.com/<your-github-username>/shopsphere.git` → *Validate*.
3. **Behaviours**
   * *Discover branches* → Strategy **All branches**.
   * *Filter by name (with regular expression)* → `user-service` (optional but tidy: this job only
     considers the branch of that name). Skip it if you want to watch discovery find all branches and
     skip the ones without a matching Jenkinsfile.
   * Do **not** add pull-request discovery for now. (If you add it later: a PR build has
     `BRANCH_NAME=PR-<n>`, so the pipeline builds and tests but skips ECR push and EKS deploy.)
4. **Build Configuration** → Mode **by Jenkinsfile** → **Script Path** `user-service/Jenkinsfile`.
5. **Scan Multibranch Pipeline Triggers** → tick *Periodically if not otherwise run* → 5 minutes
   (a safety net in case a webhook is missed).
6. **Save**. Jenkins immediately scans the repository. Open **Scan Multibranch Pipeline Log** to see
   each branch examined, which ones were accepted, and which were skipped.
7. The `user-service` branch appears under the job and builds automatically.

### Webhook (instant builds instead of 5-minute polling)

GitHub repository → Settings → Webhooks → Add webhook:
Payload URL `https://<your-jenkins-host>/github-webhook/`, content type `application/json`, events:
*Pushes* and *Branch or tag creation*. Jenkins must be reachable from GitHub. On a private network,
rely on the periodic scan.

### What the pipeline does (each Jenkinsfile, nine stages)

| # | Stage | What happens |
|---|-------|--------------|
| 1 | **Checkout** | `checkout scm`; builds tag `IMAGE_TAG = <8-char commit sha>-b<build number>` |
| 2 | **Environment validation** | Checks `AWS_REGION`/`EKS_CLUSTER_NAME`, tools on the agent, Docker access (prints the agent OS user), required files |
| 3 | **Maven build** (frontend: **Frontend build**) | `mvn clean package -DskipTests` → `target/<service>.jar`; frontend: `npm ci` + `npm run build` → `dist/`; archived |
| 4 | **Unit tests** | `mvn test` (frontend: `npm test`); JUnit results published |
| 5 | **Docker image build** | `docker build -t shopsphere-<service>:<tag> .` with commit/build labels |
| 6 | **AWS authentication** | Gets the account ID, checks the ECR repository exists, `docker login` to ECR, `aws eks update-kubeconfig`, checks deploy permission in the namespace |
| 7 | **Push image to ECR** | Tags with the full ECR URI and pushes; prints the image digest |
| 8 | **Deploy to EKS** | Replaces `__IMAGE_URI__` in `k8s/deployment.yaml`, `kubectl apply` for Service and Deployment |
| 9 | **Verify deployment** | `kubectl rollout status` (5 min timeout), confirms the running image equals the pushed one; on failure prints diagnostics and runs `kubectl rollout undo` |

Stages 6–9 run **only when `BRANCH_NAME` equals the service name** (`when { expression … }`). On any
other branch the job still builds, tests and builds the image, but never touches AWS.

Other design points:

* **No secrets in Git.** AWS keys come from Jenkins credentials and exist only inside
  `withCredentials`; Jenkins masks them in the log. Account ID and region are never hard-coded.
* **Unique tags.** `<sha>-b<build>` is traceable to a commit and unique even if the same commit is built
  twice. `latest` is never used, so `kubectl apply` always sees a changed image and rolls out.
* **Isolation.** `DOCKER_CONFIG` and `KUBECONFIG` point inside the build workspace and are deleted in
  `post { always }`, so the ECR login and cluster credentials do not stay on the agent.
* **Failure handling.** `timeout(30 min)`, `disableConcurrentBuilds()` (two builds cannot deploy the same
  service at once), error messages that say what to fix, automatic rollback on failed verification.

---

## 10. AWS ECR configuration

### 10.1 Create the repositories

Four repositories, one per branch:

* `shopsphere-user-service`
* `shopsphere-product-service`
* `shopsphere-order-service`
* `shopsphere-frontend`

```bash
export AWS_REGION=<your-region>
./scripts/create-ecr-repos.sh          # idempotent; uses the aws CLI credentials on your machine
```

The script creates each repository with **immutable tags**, **scan on push** and the lifecycle policy in
`aws/ecr-lifecycle-policy.json` (keep the 10 newest images). Equivalent single command:

```bash
aws ecr create-repository --repository-name shopsphere-user-service \
    --image-tag-mutability IMMUTABLE --image-scanning-configuration scanOnPush=true --region "$AWS_REGION"
```

### 10.2 Image naming

```
<account-id>.dkr.ecr.<region>.amazonaws.com/<repository>:<tag>

123456789012.dkr.ecr.<region>.amazonaws.com/shopsphere-user-service:1a2b3c4d-b7     (123456789012 is a dummy account id)
```

Registry = `<account-id>.dkr.ecr.<region>.amazonaws.com`, repository = `shopsphere-<service>`,
tag = `<8-char commit sha>-b<build number>`.

### 10.3 Authenticate, tag, push (what the pipeline does – you can do it by hand to learn)

```bash
export AWS_REGION=<your-region>
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# 1. Authenticate Docker to ECR. The token is valid for 12 hours; it is piped, not typed.
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

# 2. Tag the locally built image with the registry address
docker tag shopsphere-user-service:local "${REGISTRY}/shopsphere-user-service:manual-1"

# 3. Push
docker push "${REGISTRY}/shopsphere-user-service:manual-1"

# 4. Check
aws ecr describe-images --repository-name shopsphere-user-service --region "$AWS_REGION"
```

### 10.4 How Jenkins gets AWS credentials

1. **Create a dedicated IAM user** (e.g. `jenkins-shopsphere`) with **no console access**.
2. **Attach the policy** from `aws/jenkins-iam-policy.json` (create it with
   `aws iam create-policy --policy-name ShopSphereJenkins --policy-document file://aws/jenkins-iam-policy.json`,
   then `aws iam attach-user-policy`). It allows only: ECR login, push/describe on `shopsphere-*`
   repositories, and `eks:DescribeCluster`.
3. **Create an access key** for that user (`aws iam create-access-key --user-name jenkins-shopsphere`).
4. In Jenkins: Manage Jenkins → Credentials → Global → Add Credentials → Kind **AWS Credentials** →
   ID **`aws-shopsphere`** → paste Access Key ID and Secret Access Key. Delete the key from your terminal history.
5. The Jenkinsfile reads it with `withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', …]])`,
   which exports `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` **only for the steps inside the block**.
   Both the AWS CLI and the `kubectl` EKS authentication helper (`aws eks get-token`) pick them up.

The IAM user can push images and *describe* the cluster, but it still cannot touch Kubernetes until you
grant it cluster access – see [11.4](#114-let-jenkins-deploy-to-the-cluster).

Alternative worth knowing: if the agent is an EC2 instance, an **instance profile (IAM role)** removes
the need for long-lived keys. You would then delete the `withCredentials` wrapper from the Jenkinsfile.
Access keys are used here because they are the easiest to follow.

---

## 11. EKS cluster setup

Cost warning: the EKS control plane and the EC2 worker nodes are billed **by the hour while they exist**.
Do the [cleanup](#14-cleanup-instructions) when you finish a session. Check current AWS pricing.

Prerequisites on your workstation: AWS CLI v2 configured with an admin-capable identity, `eksctl`,
`kubectl`.

### 11.1 Create the cluster

```bash
export AWS_REGION=<your-region>
export EKS_CLUSTER_NAME=shopsphere-eks
eksctl create cluster \
  --name "$EKS_CLUSTER_NAME" --region "$AWS_REGION" \
  --nodegroup-name shopsphere-nodes --node-type t3.medium \
  --nodes 2 --nodes-min 2 --nodes-max 3 --managed --with-oidc
# takes ~15-20 minutes; it also writes your local kubeconfig
kubectl get nodes
```

Sizing: with 2 replicas of each of the four apps the workload requests about 1.85 vCPU and 2.9 GiB of memory
in total (plus a rolling-update surge pod), which fits two `t3.medium` nodes. If pods stay `Pending`
with `Insufficient cpu/memory`, use bigger nodes or set `replicas: 1`. If your account/region does not
offer that instance type, choose another general-purpose type.

### 11.2 Install the EBS CSI driver (required for MySQL's volume)

Without it the MySQL PersistentVolumeClaim stays `Pending` forever.

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

eksctl create iamserviceaccount \
  --name ebs-csi-controller-sa --namespace kube-system \
  --cluster "$EKS_CLUSTER_NAME" --region "$AWS_REGION" \
  --role-name AmazonEKS_EBS_CSI_DriverRole --role-only \
  --attach-policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy \
  --approve

eksctl create addon --name aws-ebs-csi-driver \
  --cluster "$EKS_CLUSTER_NAME" --region "$AWS_REGION" \
  --service-account-role-arn "arn:aws:iam::${ACCOUNT_ID}:role/AmazonEKS_EBS_CSI_DriverRole" --force

kubectl -n kube-system get pods | grep ebs-csi        # controller and node pods should be Running
```

### 11.3 ECR pull permission for the nodes

Nodes pull images from ECR using their **node IAM role**. Managed node groups created by `eksctl`
include the AWS-managed ECR read-only policy, so **no `imagePullSecrets` are needed** as long as the
repositories are in the same AWS account. (Pulling from another account or from a registry that needs a
login would need an `imagePullSecret` – not needed here.)

### 11.4 Let Jenkins deploy to the cluster

Authentication was granted by IAM (10.4); **authorisation** inside Kubernetes is granted separately with
an EKS *access entry*, limited to the `shopsphere` namespace:

```bash
JENKINS_ARN="arn:aws:iam::${ACCOUNT_ID}:user/jenkins-shopsphere"

aws eks create-access-entry --cluster-name "$EKS_CLUSTER_NAME" --region "$AWS_REGION" \
    --principal-arn "$JENKINS_ARN"

aws eks associate-access-policy --cluster-name "$EKS_CLUSTER_NAME" --region "$AWS_REGION" \
    --principal-arn "$JENKINS_ARN" \
    --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy \
    --access-scope type=namespace,namespaces=shopsphere
```

`AmazonEKSEditPolicy` allows creating and updating Deployments, Services and reading pods and logs in
that namespace – and nothing cluster-wide. This needs a cluster whose authentication mode includes the
EKS API (clusters made by recent `eksctl` versions have it). On an older cluster using the `aws-auth`
ConfigMap, map the IAM user with `eksctl create iamidentitymapping` instead.

---

## 12. Kubernetes deployment

### 12.1 Deploy the namespace, storage, secrets and database

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-storageclass-gp3.yaml
```

Create credentials interactively in your deployment environment. The script creates/updates exactly two Secrets: `mysql-credentials` for database credentials and `shopsphere-app-secrets` for the shared JWT key, internal service key and initial administrator settings. It writes no secret manifest to disk or Git:

```bash
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_PASSWORD="$(openssl rand -hex 24)"
export JWT_SECRET="$(openssl rand -hex 32)"
export SERVICE_API_KEY="$(openssl rand -hex 32)"
read -r -p "Initial admin name: " SHOPSPHERE_ADMIN_NAME
read -r -p "Initial admin email: " SHOPSPHERE_ADMIN_EMAIL
read -r -s -p "Initial admin password (10+ chars): " SHOPSPHERE_ADMIN_PASSWORD; echo
bash scripts/create-k8s-secrets.sh
```

Apply MySQL and wait for its persistent volume and readiness:

```bash
kubectl apply -f k8s/mysql/
kubectl -n shopsphere rollout status statefulset/mysql --timeout=300s
kubectl -n shopsphere get pvc
```

Deploy the three backend branches and wait for each to become Ready. Deploy `frontend` last because nginx resolves backend Service DNS names at startup. The deployments read `JWT_SECRET` and `SERVICE_API_KEY` from `shopsphere-app-secrets`; user-service additionally reads the three `SHOPSPHERE_ADMIN_*` keys. All app deployments read DB credentials from `mysql-credentials`.
### 12.2 Database configuration and persistence – explained

* **One MySQL server, three databases** (`userdb`, `productdb`, `orderdb`): each service owns its data
  and never reads another service's tables. `order-service` stores `userId`/`productId` as plain
  numbers and calls the other services over HTTP.
* The services receive their connection settings from **environment variables** in the Deployment:
  `SPRING_DATASOURCE_URL` (host `mysql`, the Service name), and username/password from the Secret
  `mysql-credentials`. Nothing sensitive is in the images or in Git.
* **Persistence:** MySQL runs as a **StatefulSet** with a `volumeClaimTemplate`. Kubernetes asks the EBS
  CSI driver for a 5 GiB **gp3 volume**, mounts it at `/var/lib/mysql`, and re-attaches the *same*
  volume if the pod restarts. Deploying new service versions never touches it.
  `WaitForFirstConsumer` creates the volume in the same Availability Zone as the node.
* The init script (a ConfigMap mounted at `/docker-entrypoint-initdb.d`) runs **only on first start
  with an empty volume**. Consequence: changing the Secret later does **not** change the password inside
  an existing database. To start fresh, delete the PVC (see Cleanup).
* **Limits of this setup:** one replica, one Availability Zone, no backups. Fine for practice. For real
  workloads use **Amazon RDS** (then only the JDBC URL and Secret change).
* `spring.jpa.hibernate.ddl-auto=update` creates tables automatically; production systems use Flyway or Liquibase.

### 12.3 What each service manifest contains

| Requirement | Where |
|-------------|-------|
| Deployment, 2 replicas, rolling update (`maxUnavailable: 0`) | `k8s/deployment.yaml` |
| ClusterIP Service | `k8s/service.yaml` |
| Resource requests/limits | 250m CPU / 384 Mi requested, 1 CPU / 768 Mi limit |
| Readiness probe | `/actuator/health/readiness` (includes a DB check, so a pod without database access receives no traffic) |
| Liveness probe | `/actuator/health/liveness` (only "is the JVM alive", so a database outage does not cause restart loops) |
| Startup probe | gives the JVM up to 3 minutes before the other probes begin |
| Environment variables | `SERVER_PORT`, `SPRING_DATASOURCE_*`; order-service also `SERVICES_USER_URL`, `SERVICES_PRODUCT_URL` |
| ECR image | `image: __IMAGE_URI__`, replaced by the pipeline with the exact ECR image of this build |
| Security | non-root, no privilege escalation, read-only root filesystem (writable `/tmp` via `emptyDir`), all capabilities dropped |

The **frontend** manifests differ in four ways: the container listens on 8080 (unprivileged nginx), its probes call
`/healthz` (they do not depend on the backends), it needs no environment variables, and its Service is
`type: LoadBalancer`. The three backend Services stay `ClusterIP`.

Why a placeholder instead of `kubectl set image`? The manifest in Git always matches what is applied, and
the account ID/region stay out of Git. The pipeline renders it with `sed` and applies it. To deploy by hand:

```bash
sed "s|__IMAGE_URI__|${REGISTRY}/shopsphere-user-service:manual-1|g" k8s/deployment.yaml | kubectl apply -f -
kubectl apply -f k8s/service.yaml
```

### 12.4 Verify the deployment and customer flow

```bash
kubectl -n shopsphere get deploy,pods,svc -o wide
kubectl -n shopsphere rollout status deployment/user-service
kubectl -n shopsphere rollout status deployment/product-service
kubectl -n shopsphere rollout status deployment/order-service
kubectl -n shopsphere rollout status deployment/frontend
```

Open the frontend LoadBalancer address and verify registration, login, catalogue browsing, cart, checkout, confirmation and order history. Sign in with the ADMIN account provisioned by `SHOPSPHERE_ADMIN_*` and verify the dashboard and management pages. These checks exercise the services through the browser proxy and shared cluster secrets. Do not call `/internal/*` APIs from a browser; they require the private service key.

### 12.5 Open the web UI

The frontend Jenkins job prints the LoadBalancer address. Read it with:

```bash
kubectl -n shopsphere get service frontend
```

Open the assigned address and sign in with a registered account or the configured initial ADMIN. Backend authorization remains enforced even if a user navigates directly to an admin URL. For safer temporary access, use port forwarding instead:

```bash
kubectl -n shopsphere port-forward svc/frontend 8080:80
```

The frontend Service uses plain HTTP in this practice setup. Restrict `loadBalancerSourceRanges` in `frontend/k8s/service.yaml` or use port forwarding. Production requires TLS and a managed ingress/load balancer configuration.

### 12.6 Rollouts and rollback

```bash
kubectl -n shopsphere rollout history deployment/user-service     # change-cause shows the Jenkins build
kubectl -n shopsphere rollout undo    deployment/user-service     # back to the previous image
kubectl -n shopsphere set image deployment/user-service user-service=<full-image-uri>   # manual pin
```

---

## 13. Troubleshooting

Start with the **first red stage** in the Jenkins console log; later errors are usually consequences.

### Jenkins and branch discovery

| Symptom | Cause and fix |
|---------|---------------|
| Branch not built / "Jenkinsfile not found" in the scan log | Script Path must be exactly `<service>/Jenkinsfile` for that job; the branch must contain the file; check the branch-name regex filter |
| `Tool type "jdk" does not have an install of "jdk21" configured` (same for `maven-3`) | Names in Manage Jenkins → Tools must match the Jenkinsfile exactly |
| `Jenkins global environment variable AWS_REGION is not set` | Add `AWS_REGION` and `EKS_CLUSTER_NAME` under Manage Jenkins → System → Global properties |
| `Could not find credentials entry with ID 'aws-shopsphere'` | Create the credential with that ID in the **Global** domain |
| `ClassNotFound … AmazonWebServicesCredentialsBinding` or the "AWS Credentials" kind is missing | Install the *CloudBees AWS Credentials* plugin and restart |
| `permission denied … /var/run/docker.sock` / "cannot use Docker" | The agent account is not in the `docker` group – [section 8.3](#83-docker-permission-for-the-agent-user-do-not-assume-jenkins); restart the agent after `usermod` |
| `required tool 'aws' / 'kubectl' / 'docker' is not installed` | Install on the **agent**, in a PATH directory such as `/usr/local/bin`; non-interactive shells do not read `~/.bashrc` |
| Webhook does nothing | Jenkins URL not reachable from GitHub, wrong path (must end `/github-webhook/`), or rely on the 5-minute scan |
| Maven: `release version 21 not supported` / `invalid target release: 21` | Build ran with an older JDK – check the `jdk21` tool and the "Environment validation" output |
| Unit tests fail only on Jenkins | Open the JUnit report; tests use H2 and need no network – look for an old JDK or a corrupted `~/.m2` on the agent |
| Build stuck for a long time at Maven | Agent has no access to Maven Central (proxy/firewall) |

### Docker

| Symptom | Cause and fix |
|---------|---------------|
| `COPY failed: file not found … target/<service>.jar` | Maven did not run in that directory, or the jar name differs from the `finalName`; run `ls <service>/target` |
| Everything is excluded from the context | `.dockerignore` only allows `target/<service>.jar` – the file name must match |
| `exec format error` in the pod | Image built on an ARM agent but the EKS nodes are x86 (or the reverse). Use the same CPU architecture for agent and nodes, or build with `docker buildx --platform` |
| Agent disk full | `docker system prune -af` on the agent; the pipeline removes the images it created, but caches grow |

### ECR

| Symptom | Cause and fix |
|---------|---------------|
| `ECR repository 'shopsphere-…' does not exist in <region>` | Run `scripts/create-ecr-repos.sh` with the **same** `AWS_REGION` Jenkins uses |
| `no basic auth credentials` on push | The `docker login` step failed or ran as another user/`DOCKER_CONFIG`; read the AWS authentication stage log |
| `AccessDeniedException` | The IAM user lacks the policy from `aws/jenkins-iam-policy.json`, or the repository name is outside `shopsphere-*` |
| `tag invalid: The image tag '…' already exists … immutable` | Tags are immutable. Happens when a Jenkins job was deleted and recreated, so build numbers restarted at 1 for the same commit. Make a new commit, or delete the old image in ECR |

### EKS and Kubernetes

| Symptom | Cause and fix |
|---------|---------------|
| `You must be logged in to the server (Unauthorized)` | No EKS access entry for the Jenkins IAM user ([11.4](#114-let-jenkins-deploy-to-the-cluster)), or `EKS_CLUSTER_NAME`/`AWS_REGION` point to a different cluster |
| `forbidden: User … cannot … in namespace` / "no permission to deploy" message | Access policy scope is wrong or the namespace `shopsphere` does not exist yet |
| Pod `ImagePullBackOff` / `ErrImagePull` | `kubectl -n shopsphere describe pod <pod>`: wrong tag, ECR in another region/account, or the node role has no ECR read policy |
| Pod `CreateContainerConfigError: secret "mysql-credentials" not found` | Create the Secret (12.1 step 2) in namespace `shopsphere` |
| Pods `Running` but `0/1 Ready` | Readiness includes the DB check: `kubectl -n shopsphere logs deploy/<svc>`; is MySQL ready and are the credentials right? |
| Pods `Pending`, "Insufficient cpu/memory" | Nodes too small: add nodes, use a larger type, or set `replicas: 1` |
| `OOMKilled` / `CrashLoopBackOff` | Raise `limits.memory`; read `kubectl logs --previous` |
| `data-mysql-0` PVC `Pending` | EBS CSI driver missing or not authorised ([11.2](#112-install-the-ebs-csi-driver-required-for-mysqls-volume)); `kubectl describe pvc data-mysql-0` |
| MySQL `Access denied for user 'shopsphere'` | The Secret password differs from the one used when the volume was first initialised. Delete the PVC and pod to reinitialise (data is lost) |
| `rollout status` times out, pipeline rolls back | Read the diagnostics printed by the failed stage; the usual cause is the database or a bad image. On the very first deploy there is no previous revision, so the rollback is a no-op |
| order-service returns `503 … is unavailable` | user-service or product-service not ready, or `SERVICES_*_URL` wrong: `kubectl -n shopsphere get pods` and `kubectl -n shopsphere exec deploy/order-service -- env \| grep SERVICES` |

### Frontend

| Symptom | Cause and fix |
|---------|---------------|
| `Tool type "nodejs" does not have an install of "node-22" configured`, or `nodejs` is an unknown tool | Install the *NodeJS* plugin and add a NodeJS tool named exactly `node-22` (Manage Jenkins → Tools) |
| `npm ci` fails with "not in sync" or an engine/version error | `package-lock.json` was not committed, or the agent's Node is older than 22.22.2. Use the `node-22` tool |
| Frontend pod `CrashLoopBackOff`, logs show `host not found in upstream "…-service"` | nginx started before one of the three Services existed. Deploy user, product and order first, then `kubectl -n shopsphere rollout restart deployment/frontend` |
| The page loads but a column says "unreachable", or the browser shows `502 Bad Gateway` for `/api/…` | That backend has no Ready pods, or its Service was deleted and recreated (nginx remembers the old IP): fix the backend, then `kubectl -n shopsphere rollout restart deployment/frontend` |
| `kubectl get service frontend` shows `<pending>` for more than about 5 minutes | `kubectl -n shopsphere describe service frontend`: the events name the reason (usually load balancer permissions or subnets) |
| The address opens but times out | `loadBalancerSourceRanges` does not include your IP address, or your network blocks outgoing port 80 |

Useful commands: `kubectl -n shopsphere describe pod <pod>`, `kubectl -n shopsphere logs <pod> --previous`,
`kubectl -n shopsphere get events --sort-by=.lastTimestamp`, `kubectl auth can-i --list -n shopsphere`.

---

## 14. Cleanup instructions

Delete things in this order so nothing keeps billing.

```bash
# 1. Kubernetes objects (and the EBS volume behind MySQL)
kubectl delete namespace shopsphere                    # removes deployments, services (incl. the frontend load balancer), MySQL, PVC
kubectl get pv                                         # should be empty; the gp3 class uses reclaimPolicy: Delete
#    Wait until the frontend's load balancer has disappeared (EC2 console -> Load Balancers) before deleting the
#    cluster: a leftover load balancer can block deletion of the VPC.
kubectl delete storageclass shopsphere-gp3

# 2. Cluster (also removes node group and its CloudFormation stacks; ~10-15 minutes)
eksctl delete cluster --name "$EKS_CLUSTER_NAME" --region "$AWS_REGION"
#    The EBS CSI role created with --role-only can be removed afterwards:
aws iam detach-role-policy --role-name AmazonEKS_EBS_CSI_DriverRole \
    --policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy
aws iam delete-role --role-name AmazonEKS_EBS_CSI_DriverRole

# 3. ECR repositories and their images
for s in user-service product-service order-service frontend; do
  aws ecr delete-repository --repository-name "shopsphere-$s" --force --region "$AWS_REGION"
done

# 4. Jenkins IAM user
aws iam list-access-keys --user-name jenkins-shopsphere        # delete each key id shown:
aws iam delete-access-key --user-name jenkins-shopsphere --access-key-id <key-id>
aws iam detach-user-policy --user-name jenkins-shopsphere \
    --policy-arn "arn:aws:iam::${ACCOUNT_ID}:policy/ShopSphereJenkins"
aws iam delete-policy --policy-arn "arn:aws:iam::${ACCOUNT_ID}:policy/ShopSphereJenkins"
aws iam delete-user --user-name jenkins-shopsphere

# 5. Jenkins: delete the four Multibranch jobs and the aws-shopsphere credential
# 6. Local: docker compose -f local/docker-compose.yml down -v ; docker system prune -af
```

Finally check the AWS console (EC2 → Volumes, Load Balancers, CloudFormation, ECR) in your region for
anything left over, and the GitHub webhook if you want to remove it.
