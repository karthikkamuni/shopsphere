# ---------------------------------------------------------------------------
# frontend image
#
# Same beginner-friendly workflow as the Java services: the build happens FIRST on the
# Jenkins agent (npm run build -> dist/), then this Dockerfile only packages the result.
# The build context is the frontend/ directory, so "COPY dist/ ..." works only after
# "npm run build" has produced dist/.
#
# Base image: the official nginx "unprivileged" variant. It already runs as the
# non-root user nginx (UID 101), keeps its pid file and temp files in /tmp, and
# listens on 8080. Its default ENTRYPOINT/CMD start nginx in the foreground, so no
# CMD is needed here.
# ---------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:stable-alpine

LABEL org.opencontainers.image.title="shopsphere-frontend" \
      org.opencontainers.image.description="ShopSphere web UI (React + Vite) served by nginx"

# nginx configuration: static files + /api proxy to the three services
COPY nginx.conf /etc/nginx/conf.d/default.conf

# The built application
COPY dist/ /usr/share/nginx/html/

# Explicit non-root user (the base image already uses it; stated here so it is obvious)
USER 101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/healthz"]
