#!/usr/bin/env bash
# Disposable, licensed development fixture. Never point this at a real home.
set -euo pipefail
if [[ $# != 4 ]]; then
    echo "Usage: $0 VERSION JAR PORT EMPTY_DIRECTORY" >&2
    exit 2
fi
version=$1
jar=$(realpath "$2")
port=$3
directory=$(realpath -m "$4")
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$port" =~ ^[0-9]+$ ]]
[[ -f "$jar" && ! -e "$directory" ]]
mkdir -m 700 -p "$directory"
curl -fsSL --retry 3 "https://maven.artifacts.atlassian.com/com/atlassian/bitbucket/server/bitbucket-it-resources/$version/bitbucket-it-resources-$version.zip" -o "$directory/fixture.zip"
unzip -q "$directory/fixture.zip" -d "$directory"
home="$directory/generated-resources/bitbucket-home"
mkdir -p "$home/shared/plugins/installed-plugins"
cp "$jar" "$home/shared/plugins/installed-plugins/sourcegraph.jar"
# Docker's image runs as UID 2003. Use sudo only if the local daemon requires it.
docker_command=(docker)
if ! docker info >/dev/null 2>&1; then
    docker_command=(sudo docker)
fi
sudo chown -R 2003:2003 "$home"
exec "${docker_command[@]}" run --rm --name "sourcegraph-bitbucket-$port" \
    -p "127.0.0.1:$port:7990" \
    -v "$home:/var/atlassian/application-data/bitbucket" \
    -e SEARCH_ENABLED=false -e JVM_MINIMUM_MEMORY=512m -e JVM_MAXIMUM_MEMORY=1536m \
    -e 'JVM_SUPPORT_RECOMMENDED_ARGS=-Datlassian.dev.mode=true -Datlassian.upm.signature.check.disabled=false' \
    "atlassian/bitbucket:$version"
