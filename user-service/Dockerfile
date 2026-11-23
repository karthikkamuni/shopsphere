# ---------------------------------------------------------------------------
# user-service image
#
# Beginner-friendly workflow: Maven builds the JAR on the Jenkins agent FIRST
# (target/user-service.jar), then this Dockerfile simply packages that JAR.
# The build context is the user-service/ directory, so "COPY target/..." works
# only after "mvn package" has produced the JAR.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="shopsphere-user-service" \
      org.opencontainers.image.description="ShopSphere User Service (Spring Boot 3.5, Java 21)"

# Fixed numeric UID/GID: Kubernetes can only verify "runAsNonRoot" for a numeric user.
RUN addgroup -g 10001 -S app \
    && adduser -u 10001 -S -G app -H -h /app app

WORKDIR /app
COPY --chown=10001:10001 target/user-service.jar /app/app.jar

# Environment-based configuration:
#   SERVER_PORT                  -> HTTP port (Spring: server.port)
#   SPRING_DATASOURCE_URL/USERNAME/PASSWORD -> database (supplied by Kubernetes)
#   JAVA_OPTS                    -> JVM flags
ENV SERVER_PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness || exit 1"]

# "exec" makes the JVM PID 1 so it receives SIGTERM and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
