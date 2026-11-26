#!/usr/bin/env python3
"""
Cross-branch consistency checker for ShopSphere.

Reads every file straight from the Git branches (no checkout needed) and checks
that service names, ports, namespace, ECR repository names, Jenkinsfile stages
and Kubernetes manifests agree with each other.

Usage (from inside the repository, with all four branches present):
    pip install pyyaml
    python3 scripts/check_consistency.py

Exit code 0 = every check passed, 1 = at least one failed.
This script cannot prove that Maven, Docker, Jenkins or AWS work - it only
catches typos and mismatches between files.
"""
import json
import re
import subprocess
import sys

import yaml

NAMESPACE = "shopsphere"
SERVICES = {
    "user-service":    {"port": 8081, "db": "userdb"},
    "product-service": {"port": 8082, "db": "productdb"},
    "order-service":   {"port": 8083, "db": "orderdb"},
}
EXPECTED_STAGES = [
    "Checkout",
    "Environment validation",
    "Maven build",
    "Unit tests",
    "Docker image build",
    "AWS authentication",
    "Push image to ECR",
    "Deploy to EKS",
    "Verify deployment",
]

FRONTEND_STAGES = [
    "Checkout",
    "Environment validation",
    "Frontend build",
    "Unit tests",
    "Docker image build",
    "AWS authentication",
    "Push image to ECR",
    "Deploy to EKS",
    "Verify deployment",
]
# URL prefixes owned by each backend and forwarded by frontend nginx.
API_PATHS = {
    "user-service": ["auth", "users"],
    "product-service": ["products"],
    "order-service": ["cart", "orders", "admin"],
}

results = []


def check(name, ok, detail=""):
    results.append((ok, name, detail))


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


def show(branch, path):
    return git("show", f"{branch}:{path}")


def tree(branch):
    return [p for p in git("ls-tree", "-r", "--name-only", branch).splitlines() if p]


def env_map(container):
    return {e["name"]: e for e in container.get("env", [])}


def check_service(svc, cfg):
    port, db = cfg["port"], cfg["db"]
    ecr = f"shopsphere-{svc}"
    files = tree(svc)

    # --- branch layout -------------------------------------------------------
    top = sorted({f.split("/")[0] for f in files})
    check(f"[{svc}] branch contains only the '{svc}/' directory", top == [svc], f"top-level: {top}")
    for required in ["pom.xml", "Dockerfile", "Jenkinsfile", "k8s/deployment.yaml", "k8s/service.yaml"]:
        check(f"[{svc}] has {svc}/{required}", f"{svc}/{required}" in files)
    check(f"[{svc}] has Java sources and tests",
          any("/src/main/java/" in f for f in files) and any("/src/test/java/" in f for f in files))
    check(f"[{svc}] no build output committed", not any("/target/" in f for f in files))

    # --- Jenkinsfile ---------------------------------------------------------
    jf = show(svc, f"{svc}/Jenkinsfile")
    check(f"[{svc}] Jenkinsfile SERVICE_NAME is '{svc}'", f"SERVICE_NAME       = '{svc}'" in jf)
    check(f"[{svc}] Jenkinsfile ECR_REPOSITORY is '{ecr}'", f"ECR_REPOSITORY     = '{ecr}'" in jf)
    check(f"[{svc}] Jenkinsfile K8S_NAMESPACE is '{NAMESPACE}'", f"K8S_NAMESPACE      = '{NAMESPACE}'" in jf)
    stages = re.findall(r"stage\('([^']+)'\)", jf)
    check(f"[{svc}] Jenkinsfile has the 9 required stages in order", stages == EXPECTED_STAGES, f"found: {stages}")
    check(f"[{svc}] Jenkinsfile is declarative", jf.lstrip().startswith("//") and "pipeline {" in jf)
    check(f"[{svc}] Jenkinsfile uses Jenkins credentials binding",
          "AmazonWebServicesCredentialsBinding" in jf and "credentialsId" in jf)
    check(f"[{svc}] Jenkinsfile tag contains the git commit sha", "IMAGE_TAG" in jf and "GIT_COMMIT_SHORT}-b" in jf)
    check(f"[{svc}] Jenkinsfile deploys only on branch '{svc}'", jf.count("env.BRANCH_NAME == env.SERVICE_NAME") == 4)
    check(f"[{svc}] Jenkinsfile only touches its own deployment",
          all(other not in jf for other in [*SERVICES, "frontend"] if other != svc))
    secrets_found = re.findall(r"AKIA[0-9A-Z]{16}|\b\d{12}\b|\b[a-z]{2}-[a-z]+-\d\b|secret_?key\s*=\s*['\"][^'\"]+['\"]", jf)
    check(f"[{svc}] Jenkinsfile has no hard-coded account id / region / keys", not secrets_found, str(secrets_found))

    # --- pom.xml -------------------------------------------------------------
    pom = show(svc, f"{svc}/pom.xml")
    check(f"[{svc}] pom artifactId is '{svc}'", f"<artifactId>{svc}</artifactId>" in pom)
    check(f"[{svc}] pom java.version is 21", "<java.version>21</java.version>" in pom)
    check(f"[{svc}] pom Spring Boot parent is 3.5.x", re.search(r"<version>3\.5\.\d+</version>", pom) is not None)
    check(f"[{svc}] pom finalName gives target/{svc}.jar", "<finalName>${project.artifactId}</finalName>" in pom)

    # --- Dockerfile ----------------------------------------------------------
    df = show(svc, f"{svc}/Dockerfile")
    check(f"[{svc}] Dockerfile uses a Java 21 base image", re.search(r"^FROM eclipse-temurin:21-", df, re.M) is not None)
    check(f"[{svc}] Dockerfile copies target/{svc}.jar", f"target/{svc}.jar" in df)
    check(f"[{svc}] Dockerfile exposes {port}", re.search(rf"^EXPOSE {port}$", df, re.M) is not None)
    check(f"[{svc}] Dockerfile sets SERVER_PORT={port}", f"SERVER_PORT={port}" in df)
    check(f"[{svc}] Dockerfile runs as numeric non-root user", re.search(r"^USER 10001:10001$", df, re.M) is not None)
    check(f"[{svc}] Dockerfile has an ENTRYPOINT", re.search(r"^ENTRYPOINT \[", df, re.M) is not None)
    check(f"[{svc}] .dockerignore only lets the jar in", f"!target/{svc}.jar" in show(svc, f"{svc}/.dockerignore"))

    # --- application.properties ---------------------------------------------
    props = show(svc, f"{svc}/src/main/resources/application.properties")
    check(f"[{svc}] application.properties server.port={port}", re.search(rf"^server\.port={port}$", props, re.M) is not None)
    check(f"[{svc}] application.properties default DB is '{db}'", f"/{db}?" in props)
    check(f"[{svc}] application.properties has no password", not re.search(r"^spring\.datasource\.password\s*=\s*\S", props, re.M))

    # --- Deployment ----------------------------------------------------------
    dep = yaml.safe_load(show(svc, f"{svc}/k8s/deployment.yaml"))
    meta, spec = dep["metadata"], dep["spec"]
    c = spec["template"]["spec"]["containers"][0]
    envs = env_map(c)
    check(f"[{svc}] Deployment kind/name/namespace", dep["kind"] == "Deployment" and meta["name"] == svc and meta["namespace"] == NAMESPACE)
    check(f"[{svc}] Deployment selector matches pod labels",
          spec["selector"]["matchLabels"]["app"] == svc and spec["template"]["metadata"]["labels"]["app"] == svc)
    check(f"[{svc}] container name is '{svc}'", c["name"] == svc)
    check(f"[{svc}] image is the __IMAGE_URI__ placeholder", c["image"] == "__IMAGE_URI__")
    check(f"[{svc}] containerPort is {port}", c["ports"][0]["containerPort"] == port)
    check(f"[{svc}] SERVER_PORT env is {port}", envs["SERVER_PORT"]["value"] == str(port))
    check(f"[{svc}] JDBC URL points at host 'mysql' and database '{db}'",
          envs["SPRING_DATASOURCE_URL"]["value"].startswith(f"jdbc:mysql://mysql:3306/{db}?"))
    check(f"[{svc}] DB credentials come from Secret 'mysql-credentials'",
          envs["SPRING_DATASOURCE_USERNAME"]["valueFrom"]["secretKeyRef"]["name"] == "mysql-credentials"
          and envs["SPRING_DATASOURCE_PASSWORD"]["valueFrom"]["secretKeyRef"]["name"] == "mysql-credentials")
    for probe, path in [("readinessProbe", "/actuator/health/readiness"),
                        ("livenessProbe", "/actuator/health/liveness"),
                        ("startupProbe", "/actuator/health/liveness")]:
        check(f"[{svc}] {probe} -> {path}", c[probe]["httpGet"]["path"] == path)
    check(f"[{svc}] resource requests and limits set",
          all(k in c["resources"]["requests"] for k in ("cpu", "memory"))
          and all(k in c["resources"]["limits"] for k in ("cpu", "memory")))
    check(f"[{svc}] container is non-root / no privilege escalation",
          spec["template"]["spec"]["securityContext"]["runAsNonRoot"] is True
          and c["securityContext"]["allowPrivilegeEscalation"] is False)
    for key in ["JWT_SECRET", "SERVICE_API_KEY"]:
        ref = envs.get(key, {}).get("valueFrom", {}).get("secretKeyRef", {})
        check(f"[{svc}] {key} comes from shopsphere-app-secrets/{key}",
              ref.get("name") == "shopsphere-app-secrets" and ref.get("key") == key)
    if svc == "user-service":
        for key in ["SHOPSPHERE_ADMIN_NAME", "SHOPSPHERE_ADMIN_EMAIL", "SHOPSPHERE_ADMIN_PASSWORD"]:
            ref = envs.get(key, {}).get("valueFrom", {}).get("secretKeyRef", {})
            check(f"[user-service] {key} comes from shopsphere-app-secrets/{key}",
                  ref.get("name") == "shopsphere-app-secrets" and ref.get("key") == key)

    # --- Service -------------------------------------------------------------
    svc_y = yaml.safe_load(show(svc, f"{svc}/k8s/service.yaml"))
    check(f"[{svc}] Service name/namespace/type",
          svc_y["metadata"]["name"] == svc and svc_y["metadata"]["namespace"] == NAMESPACE
          and svc_y["spec"]["type"] == "ClusterIP")
    check(f"[{svc}] Service selector targets '{svc}'", svc_y["spec"]["selector"] == {"app": svc})
    check(f"[{svc}] Service port is {port}", svc_y["spec"]["ports"][0]["port"] == port)

    return envs


def check_frontend():
    name, ecr, tag = "frontend", "shopsphere-frontend", "[frontend]"
    files = tree(name)

    # --- branch layout -------------------------------------------------------
    top = sorted({f.split("/")[0] for f in files})
    check(f"{tag} branch contains only the 'frontend/' directory", top == [name], f"top-level: {top}")
    for required in ["package.json", "package-lock.json", "vite.config.js", "index.html", "nginx.conf",
                     "Dockerfile", ".dockerignore", "Jenkinsfile", "src/App.jsx", "src/App.test.jsx",
                     "k8s/deployment.yaml", "k8s/service.yaml"]:
        check(f"{tag} has frontend/{required}", f"frontend/{required}" in files)
    check(f"{tag} no node_modules, dist or test-results committed",
          not any(re.search(r"/(node_modules|dist|test-results)/", f) for f in files))

    # --- Jenkinsfile ---------------------------------------------------------
    jf = show(name, "frontend/Jenkinsfile")
    check(f"{tag} Jenkinsfile SERVICE_NAME is 'frontend'", "SERVICE_NAME       = 'frontend'" in jf)
    check(f"{tag} Jenkinsfile ECR_REPOSITORY is '{ecr}'", f"ECR_REPOSITORY     = '{ecr}'" in jf)
    check(f"{tag} Jenkinsfile K8S_NAMESPACE is '{NAMESPACE}'", f"K8S_NAMESPACE      = '{NAMESPACE}'" in jf)
    stages = re.findall(r"stage\('([^']+)'\)", jf)
    check(f"{tag} Jenkinsfile has the 9 required stages in order", stages == FRONTEND_STAGES, f"found: {stages}")
    check(f"{tag} Jenkinsfile is declarative", jf.lstrip().startswith("//") and "pipeline {" in jf)
    check(f"{tag} Jenkinsfile uses the NodeJS tool 'node-22'", "nodejs 'node-22'" in jf)
    check(f"{tag} Jenkinsfile runs npm ci, npm run build and npm test",
          all(cmd in jf for cmd in ("npm ci", "npm run build", "npm test")))
    check(f"{tag} Jenkinsfile uses Jenkins credentials binding",
          "AmazonWebServicesCredentialsBinding" in jf and "credentialsId" in jf)
    check(f"{tag} Jenkinsfile tag contains the git commit sha", "IMAGE_TAG" in jf and "GIT_COMMIT_SHORT}-b" in jf)
    check(f"{tag} Jenkinsfile deploys only on branch 'frontend'", jf.count("env.BRANCH_NAME == env.SERVICE_NAME") == 4)
    check(f"{tag} Jenkinsfile never mentions the backend services", all(s not in jf for s in SERVICES))
    secrets_found = re.findall(r"AKIA[0-9A-Z]{16}|\b\d{12}\b|\b[a-z]{2}-[a-z]+-\d\b|secret_?key\s*=\s*['\"][^'\"]+['\"]", jf)
    check(f"{tag} Jenkinsfile has no hard-coded account id / region / keys", not secrets_found, str(secrets_found))

    # --- package.json and lock file ------------------------------------------
    pkg = json.loads(show(name, "frontend/package.json"))
    check(f"{tag} package.json has build and test scripts",
          pkg["scripts"].get("build") == "vite build" and pkg["scripts"].get("test") == "vitest run")
    check(f"{tag} package.json requires Node >=22.12", pkg.get("engines", {}).get("node") == ">=22.12")
    lock_root = json.loads(show(name, "frontend/package-lock.json"))["packages"][""]
    check(f"{tag} package-lock.json declares the same dependencies as package.json (npm ci will accept it)",
          {**pkg.get("dependencies", {}), **pkg.get("devDependencies", {})}
          == {**lock_root.get("dependencies", {}), **lock_root.get("devDependencies", {})})

    # --- Dockerfile and .dockerignore ----------------------------------------
    df = show(name, "frontend/Dockerfile")
    check(f"{tag} Dockerfile uses the unprivileged nginx base image",
          re.search(r"^FROM nginxinc/nginx-unprivileged:", df, re.M) is not None)
    check(f"{tag} Dockerfile copies dist/ and nginx.conf",
          "COPY dist/ /usr/share/nginx/html/" in df and "COPY nginx.conf /etc/nginx/conf.d/default.conf" in df)
    check(f"{tag} Dockerfile exposes 8080 and runs as user 101",
          re.search(r"^EXPOSE 8080$", df, re.M) is not None and re.search(r"^USER 101$", df, re.M) is not None)
    check(f"{tag} .dockerignore only lets dist and nginx.conf in",
          show(name, "frontend/.dockerignore").splitlines()[-3:] == ["*", "!dist", "!nginx.conf"])

    # --- nginx.conf: proxy targets must match the real services ---------------
    conf = show(name, "frontend/nginx.conf")
    check(f"{tag} nginx listens on 8080", re.search(r"^\s*listen 8080;", conf, re.M) is not None)
    for svc, cfg in SERVICES.items():
        for prefix in API_PATHS[svc]:
            check(f"{tag} nginx forwards /api/{prefix} to {svc}:{cfg['port']}",
                  re.search(rf"location /api/{prefix}\s*\{{\s*proxy_pass http://{svc}:{cfg['port']};", conf) is not None)
    check(f"{tag} nginx has its own /healthz location", "location = /healthz" in conf)
    conf_no_comments = re.sub(r"(?m)^\s*#.*$", "", conf)   # comments may mention 'actuator' on purpose
    check(f"{tag} nginx never forwards actuator endpoints", "actuator" not in conf_no_comments)
    check(f"{tag} nginx falls back to index.html (single-page app)", "try_files $uri /index.html;" in conf)
    check(f"{tag} nginx add_header only at server level (location-level add_header would drop the security headers)",
          not re.search(r"^ {8,}add_header", conf, re.M))

    # --- Deployment ----------------------------------------------------------
    dep = yaml.safe_load(show(name, "frontend/k8s/deployment.yaml"))
    spec = dep["spec"]
    pod = spec["template"]["spec"]
    c = pod["containers"][0]
    port = c["ports"][0]
    check(f"{tag} Deployment kind/name/namespace",
          dep["kind"] == "Deployment" and dep["metadata"]["name"] == name and dep["metadata"]["namespace"] == NAMESPACE)
    check(f"{tag} Deployment selector matches pod labels",
          spec["selector"]["matchLabels"]["app"] == name and spec["template"]["metadata"]["labels"]["app"] == name)
    check(f"{tag} container name is 'frontend' and image is the __IMAGE_URI__ placeholder",
          c["name"] == name and c["image"] == "__IMAGE_URI__")
    check(f"{tag} containerPort is 8080 (named 'http')", port["containerPort"] == 8080 and port["name"] == "http")
    check(f"{tag} readiness and liveness probes use /healthz",
          c["readinessProbe"]["httpGet"]["path"] == "/healthz" and c["livenessProbe"]["httpGet"]["path"] == "/healthz")
    check(f"{tag} resource requests and limits set",
          all(k in c["resources"]["requests"] for k in ("cpu", "memory"))
          and all(k in c["resources"]["limits"] for k in ("cpu", "memory")))
    check(f"{tag} runs as non-root user 101 with a read-only root filesystem and a writable /tmp",
          pod["securityContext"]["runAsNonRoot"] is True and pod["securityContext"]["runAsUser"] == 101
          and c["securityContext"]["readOnlyRootFilesystem"] is True
          and any(m["mountPath"] == "/tmp" for m in c["volumeMounts"]))

    # --- Service -------------------------------------------------------------
    svc_y = yaml.safe_load(show(name, "frontend/k8s/service.yaml"))
    check(f"{tag} Service name/namespace and type LoadBalancer (the only public Service)",
          svc_y["metadata"]["name"] == name and svc_y["metadata"]["namespace"] == NAMESPACE
          and svc_y["spec"]["type"] == "LoadBalancer")
    check(f"{tag} Service selects 'frontend' and maps port 80 to the container's named port",
          svc_y["spec"]["selector"] == {"app": name} and svc_y["spec"]["ports"][0]["port"] == 80
          and svc_y["spec"]["ports"][0]["targetPort"] == port["name"])


def main():
    envs = {}
    for svc, cfg in SERVICES.items():
        envs[svc] = check_service(svc, cfg)
    check_frontend()

    # --- order-service -> other services (internal communication) ------------
    o = envs["order-service"]
    check("[order-service] SERVICES_USER_URL = http://user-service:8081",
          o["SERVICES_USER_URL"]["value"] == f"http://user-service:{SERVICES['user-service']['port']}")
    check("[order-service] SERVICES_PRODUCT_URL = http://product-service:8082",
          o["SERVICES_PRODUCT_URL"]["value"] == f"http://product-service:{SERVICES['product-service']['port']}")
    order_api = show("order-service", "order-service/API.md")
    user_api = show("user-service", "user-service/API.md")
    product_api = show("product-service", "product-service/API.md")
    check("[user-service] documents the trusted minimal customer detail endpoint",
          "GET /internal/users/{id}" in user_api and "{id,name,email,active}" in user_api)
    check("[order-service] consumes trusted customer details for admin views",
          "customer:{id,name,email,active}" in order_api and "SERVICES_USER_URL" in order_api)
    check("[product-service] documents inactive product listing and reactivation",
          "GET /products/admin/inactive" in product_api and "PATCH /products/{id}/reactivate" in product_api)
    check("[product-service] documents stock reservation reaper and lease",
          "scheduled reaper" in product_api and "RESERVATION_LEASE_MS" in product_api)

    # --- main branch ---------------------------------------------------------
    main_files = tree("main")
    for required in ["README.md", "k8s/00-namespace.yaml", "k8s/01-storageclass-gp3.yaml",
                     "k8s/mysql/mysql-statefulset.yaml", "k8s/mysql/mysql-service.yaml",
                     "k8s/mysql/mysql-init-configmap.yaml", "aws/jenkins-iam-policy.json",
                     "aws/ecr-lifecycle-policy.json", "scripts/create-ecr-repos.sh",
                     "local/docker-compose.yml", "local/init-databases.sh", "scripts/create-k8s-secrets.sh",
                     "docs/ARCHITECTURE.md"]:
        check(f"[main] has {required}", required in main_files)
    check("[main] contains no service source code",
          not any(f.startswith(tuple(f"{s}/" for s in [*SERVICES, "frontend"])) for f in main_files))

    ns = yaml.safe_load(show("main", "k8s/00-namespace.yaml"))
    check("[main] namespace manifest is 'shopsphere'", ns["kind"] == "Namespace" and ns["metadata"]["name"] == NAMESPACE)

    sc = yaml.safe_load(show("main", "k8s/01-storageclass-gp3.yaml"))
    sts = yaml.safe_load(show("main", "k8s/mysql/mysql-statefulset.yaml"))
    check("[main] StatefulSet uses the StorageClass that exists",
          sts["spec"]["volumeClaimTemplates"][0]["spec"]["storageClassName"] == sc["metadata"]["name"])
    check("[main] MySQL Service name is 'mysql' (matches the JDBC URLs)",
          yaml.safe_load(show("main", "k8s/mysql/mysql-service.yaml"))["metadata"]["name"] == "mysql")
    mysql_env = env_map(sts["spec"]["template"]["spec"]["containers"][0])
    secret_keys = {e["valueFrom"]["secretKeyRef"]["key"] for e in mysql_env.values()}
    check("[main] MySQL reads MYSQL_ROOT_PASSWORD/MYSQL_USER/MYSQL_PASSWORD from 'mysql-credentials'",
          secret_keys == {"MYSQL_ROOT_PASSWORD", "MYSQL_USER", "MYSQL_PASSWORD"})

    cm = yaml.safe_load(show("main", "k8s/mysql/mysql-init-configmap.yaml"))
    check("[main] ConfigMap init script is identical to local/init-databases.sh",
          cm["data"]["init-databases.sh"] == show("main", "local/init-databases.sh"))
    init_sql = cm["data"]["init-databases.sh"]
    check("[main] init script creates every database the services use",
          all(f"CREATE DATABASE IF NOT EXISTS {cfg['db']}" in init_sql for cfg in SERVICES.values()))

    # Every manifest namespace is the same
    namespaces = {yaml.safe_load(show("main", p))["metadata"].get("namespace")
                  for p in ["k8s/mysql/mysql-statefulset.yaml", "k8s/mysql/mysql-service.yaml",
                            "k8s/mysql/mysql-init-configmap.yaml"]}
    check("[main] all MySQL manifests are in namespace 'shopsphere'", namespaces == {NAMESPACE}, str(namespaces))

    readme = show("main", "README.md")
    for svc in SERVICES:
        check(f"[main] README mentions shopsphere-{svc}", f"shopsphere-{svc}" in readme)
        check(f"[main] README mentions Script Path {svc}/Jenkinsfile", f"{svc}/Jenkinsfile" in readme)

    check("[main] README mentions shopsphere-frontend", "shopsphere-frontend" in readme)
    check("[main] README mentions Script Path frontend/Jenkinsfile", "frontend/Jenkinsfile" in readme)
    check("[main] create-ecr-repos.sh creates all four repositories",
          "for service in user-service product-service order-service frontend; do" in show("main", "scripts/create-ecr-repos.sh"))
    secret_script = show("main", "scripts/create-k8s-secrets.sh")
    check("[main] secret helper requires shared JWT_SECRET and SERVICE_API_KEY",
          all(name in secret_script for name in ["JWT_SECRET", "SERVICE_API_KEY", "shopsphere-app-secrets"]))
    compose = show("main", "local/docker-compose.yml")
    check("[main] Compose defines all five runtime services and persistent MySQL data",
          all(f"  {name}:" in compose for name in ["mysql", *SERVICES, "frontend"])
          and "mysql-data:/var/lib/mysql" in compose and "mysql-data:" in compose)
    check("[main] Compose shares the JWT/service keys with all three backends",
          compose.count("${JWT_SECRET:?Set a shared JWT_SECRET") == 3
          and compose.count("${SERVICE_API_KEY:?Set a shared SERVICE_API_KEY") == 3)

    # --- report --------------------------------------------------------------
    failed = [r for r in results if not r[0]]
    for ok, name, detail in results:
        print(("PASS  " if ok else "FAIL  ") + name + (f"   <- {detail}" if (not ok and detail) else ""))
    print(f"\n{len(results) - len(failed)} passed, {len(failed)} failed, {len(results)} checks total")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
