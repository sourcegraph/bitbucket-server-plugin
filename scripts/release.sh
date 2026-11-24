#!/usr/bin/env bash
# Archive a published 4.x Marketplace JAR as an immutable versioned GCS object.
# Checks only by default; uploading requires an explicit --upload.
set -euo pipefail
if [[ $# -lt 3 || $# -gt 4 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ! "$2" =~ ^[0-9]+$ ||
      ! "$3" =~ ^[0-9a-f]{64}$ || ( $# == 4 && "$4" != --upload ) ]]; then
    echo "Usage: $0 VERSION MARKETPLACE_BUILD SHA256 [--upload]" >&2
    echo "Example: $0 4.0.1 2010091 689a2e7842252d3da99e4be8eec84428f9ecac2438893a3923bfc54fbf6ad55f" >&2
    exit 2
fi
version=$1 build=$2 expected=$3
# latest.jar and the 3.x archives are the Bitbucket 9 / javax line; this script
# never writes them. Publish 3.x patches by hand as described in the 3.x branch.
[[ "$version" != 3.* ]] || { echo "Refusing 3.x: $version" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
jar="$work/$version.jar"
# Archive the bytes customers download from Marketplace, not a local rebuild.
curl -fsSL --retry 3 -o "$jar" "https://marketplace.atlassian.com/download/apps/1231975/version/$build"
actual=$( (command -v sha256sum >/dev/null && sha256sum "$jar" || shasum -a 256 "$jar") | cut -d' ' -f1)
[[ "$actual" == "$expected" ]] || { echo "SHA-256 mismatch for build $build: $actual" >&2; exit 1; }
bundle=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Bundle-Version: //p')
[[ "$bundle" == "$version" ]] || { echo "Build $build is version $bundle, not $version" >&2; exit 1; }
echo "Marketplace build $build is $version with SHA-256 $actual"

destination="gs://sourcegraph-for-bitbucket-server/$version.jar"
if [[ ${4:-} != --upload ]]; then
    echo "Not uploaded. Rerun with --upload to create $destination."
    exit 0
fi
# Generation 0 means the object must not exist, so published bytes never change.
gcloud storage cp --if-generation-match=0 --predefined-acl=publicRead \
    --content-type=application/java-archive "$jar" "$destination"
echo "Uploaded https://storage.googleapis.com/sourcegraph-for-bitbucket-server/$version.jar; latest.jar is unchanged."
