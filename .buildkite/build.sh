#!/usr/bin/env bash
set -euo pipefail
docker run --rm -v "$PWD:/workspace" -w /workspace \
    -u "$(id -u):$(id -g)" -e MAVEN_CONFIG=/tmp/maven \
    maven:3.9.9-eclipse-temurin-21 \
    mvn -B -ntp -Dmaven.repo.local=/tmp/maven/repository clean verify
