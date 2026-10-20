#!/usr/bin/env bash
set -euo pipefail
# The agent and Docker daemon may not share a filesystem (Buildkite sidecar).
container=$(docker create -w /workspace \
    maven:3.9.9-eclipse-temurin-21 \
    mvn -B -ntp clean verify)
trap 'docker rm -f "$container" >/dev/null' EXIT
tar -cf - pom.xml .mvn src | docker cp - "$container:/workspace"
docker start -a "$container"
[[ $(docker inspect --format '{{.State.ExitCode}}' "$container") == 0 ]]
docker cp "$container:/workspace/target" .
