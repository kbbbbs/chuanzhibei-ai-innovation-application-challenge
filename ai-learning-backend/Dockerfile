FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /build
COPY ai-learning-backend/pom.xml .
COPY ai-learning-backend/src ./src
ARG MAVEN_OFFLINE=false
RUN --mount=type=cache,id=eduloop-maven-repository,target=/root/.m2/repository,sharing=locked \
    set -eu; \
    case "$MAVEN_OFFLINE" in true) offline=-o ;; false) offline= ;; *) echo 'MAVEN_OFFLINE must be true or false'; exit 2 ;; esac; \
    echo '[backend build] Compiling and packaging repository sources with Maven'; \
    (while sleep 30; do echo '[backend build] Maven is still running'; done) & \
    heartbeat=$!; \
    trap 'kill "$heartbeat" 2>/dev/null || true' EXIT INT TERM; \
    mvn $offline -B -e -Dstyle.color=never \
      -Daether.connector.connectTimeout=15000 \
      -Daether.connector.requestTimeout=60000 \
      -DskipTests package; \
    echo '[backend build] Source package completed'

FROM eclipse-temurin:21.0.9_10-jdk-noble@sha256:f3f6ba56a9ab68c1e99310d3b06bd1279ed03db00c581d8e22bb2e65d6ce8fa4
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home app \
    && mkdir -p /app/config/private /app/data/uploads /app/data/rag \
    && chown -R app:app /app \
    && chmod 700 /app/config/private
WORKDIR /app
COPY --from=build --chown=app:app /build/target/ai-learning-backend-0.0.1-SNAPSHOT.jar /app/backend.jar
USER 10001:10001
ENV AI_INSTALLATION_DIR=/app/config/private \
    UPLOAD_DIR=/app/data/uploads \
    RAG_STORAGE_PATH=/app/data/rag
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
