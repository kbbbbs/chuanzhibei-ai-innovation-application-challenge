FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
# Context is the sanitized dependency-only directory, never the host .m2 root.
RUN --mount=type=bind,source=.,target=/seed,readonly \
    --mount=type=cache,id=eduloop-maven-repository,target=/root/.m2/repository,sharing=locked \
    set -eu; \
    cp -an /seed/. /root/.m2/repository/; \
    echo '[Maven cache] Dependency-only seed copied to BuildKit repository cache'
