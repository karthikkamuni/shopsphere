# Validation report

This file says exactly what was checked, with which tool, and what was **not** checked. Nothing here is
an invented test result: the checks listed as "done" were run on the committed files of all four branches.

## A. Checks that were run

| # | Check | Tool | Result |
|---|-------|------|--------|
| 1 | Dockerfile lint (3 files) | hadolint 2.15.1 | Clean. (It first flagged `HEALTHCHECK … CMD` shell form, rule DL3025; changed to JSON form.) |
| 2 | Kubernetes schema, strict mode (all 11 distinct manifests: 3 Deployments, 3 Services, Namespace, StorageClass, MySQL StatefulSet/Service/ConfigMap) | kubeconform 0.8.0 | 11 valid, 0 invalid. Deployments were checked both with the `__IMAGE_URI__` placeholder and rendered with a sample image. Validated against the tool's default Kubernetes schema version, not necessarily your cluster's. |
| 3 | Jenkinsfile Groovy syntax (3 files) | npm-groovy-lint | 0 errors. 8 warnings per file, all `GStringExpressionWithinString`: shell snippets inside single-quoted Groovy strings, where `${VAR}` is intentionally expanded by the **shell**, not by Groovy. |
| 4 | Jenkinsfiles identical except for service names | `diff` after name substitution | user = product = order |
| 5 | `pom.xml` files well-formed (3) | Python XML parser | OK (this does **not** resolve dependencies) |
| 6 | Shell scripts parse | `bash -n` | OK |
| 7 | JSON policies parse | Python `json` | OK |
| 8 | `docker-compose.yml` | `docker compose config` | Valid with the two password variables set; refuses to render when they are missing (as designed) |
| 9 | README internal links | script | 22 anchors, 0 broken; linked files exist |
| 10 | **Cross-branch consistency** (`scripts/check_consistency.py`) | Python + `git show` | **172 checks, 172 passed.** |
| 11 | Secret / account-id / region scan of all four branches | `git grep` | No AWS keys, no 12-digit account IDs and no region names found (the only account-like number is the dummy `123456789012` in the README) |

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

**The checker itself was tested.** On a throwaway clone, three deliberate mistakes were injected (product
pipeline pointing at `user-service`, order-service `containerPort` changed, wrong namespace in a Service). It
reported 4 failing checks covering all three, so a green result is meaningful.

## B. What could NOT be validated here – do these on your own machines

The sandbox used to build this repository has **no Maven, no reachable Maven Central, no Docker daemon, no
Docker Hub access, no Jenkins and no AWS account**. Therefore:

| Not verified | Why it matters | How to verify |
|--------------|----------------|---------------|
| **Maven build and unit tests** – `mvn package` / `mvn test` were never run; the Java code was **not compiled**. (A parse-only `javac` pass was run but can only report unresolved imports without Spring on the classpath, so it proves nothing.) There are 20 tests: 6 user, 6 product, 8 order. | The code was written carefully against Spring Boot 3.5 / Spring Framework 6.2 APIs, but a compile error or a failing test is still possible. Also, `spring-boot-starter-parent:3.5.16` is expected to be on Maven Central but this could not be confirmed from here. | `cd <service> && mvn -B clean package` on each branch. **Do this first.** If a test fails, fix it on that branch before wiring up Jenkins. |
| **Docker build and run** | The base image tag `eclipse-temurin:21-jre-alpine`, the Alpine `addgroup`/`adduser` flags and the JVM starting under a read-only root filesystem were not exercised. | Section 7 of the README. Then run the container with MySQL and call `/actuator/health`. |
| **Jenkins execution** | The declarative syntax was only linted as Groovy. Plugin behaviour (`tools {}`, `AmazonWebServicesCredentialsBinding`, `when { expression }`, `post`) was never executed. | Ask Jenkins to validate a Jenkinsfile: `curl -u <user>:<api-token> -X POST -F "jenkinsfile=<user-service/Jenkinsfile" "$JENKINS_URL/pipeline-model-converter/validate"`, then run the real job. |
| **AWS** – ECR push, `aws eks update-kubeconfig`, IAM policy sufficiency, EKS access entry and `AmazonEKSEditPolicy` permissions (including the pipeline's `kubectl auth can-i` check), `eksctl` and EBS CSI add-on commands | All commands follow AWS documentation but were never run against an account. | Follow README sections 10–11. If a permission is missing, the pipeline stops in "AWS authentication" with a message naming it. |
| **Kubernetes runtime behaviour** – probes, MySQL first-start init script, EBS volume binding, rolling update, rollback | Only the YAML *schema* was validated. | README section 12.4; `kubectl get pods`, `kubectl describe`. |
| **Real Multibranch discovery** | Needs your GitHub repository and Jenkins. | README section 9; read the *Scan Multibranch Pipeline Log*. |

Known simplifications (not defects): single MySQL pod without backups, `ddl-auto=update`, no public ingress,
IAM user access key instead of an IAM role for Jenkins, Spring Boot 3.5 being at the end of open-source support.
