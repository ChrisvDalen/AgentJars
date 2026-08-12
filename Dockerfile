# Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so a source-only change reuses the cached layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package \
    && cp target/agentjars-webapp-*.jar app.jar

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app

# The deploy endpoint clones repositories and writes bundles; give it a writable home.
RUN useradd --system --create-home --uid 10001 agentjars \
    && mkdir -p /var/lib/agentjars \
    && chown agentjars:agentjars /var/lib/agentjars
USER agentjars

COPY --from=build /workspace/app.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENV AGENTJARS_DEPLOY_WORKDIR=/var/lib/agentjars

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
    CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
