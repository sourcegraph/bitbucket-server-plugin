#!/usr/bin/env bash
# Stage the already-tested JAR. Publication requires an explicit --upload.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ( $# == 2 && "$2" != --upload ) ]]; then
    echo "Usage: $0 VERSION [--upload] (from the repository root)" >&2
    exit 2
fi
version=$1
jar="target/sourcegraph-bitbucket-$version.jar"
[[ -f "$jar" ]] || { echo "Build and runtime-test $jar first; see DEVELOPMENT.md." >&2; exit 1; }
actual=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Bundle-Version: //p')
[[ "$actual" == "$version" ]] || { echo "JAR version mismatch: $actual" >&2; exit 1; }
sha256sum "$jar"
if [[ ${2:-} != --upload ]]; then
    echo "Not published. Obtain release approval before --upload or Marketplace submission."
    exit 0
fi

# Never replace latest.jar: it is the Bitbucket 9 / javax artifact. An immutable
# versioned object also prevents changing bytes after signing or verification.
destination="gs://sourcegraph-for-bitbucket-server/$version.jar"
gsutil -h 'x-goog-if-generation-match:0' cp "$jar" "$destination"
gsutil acl ch -u AllUsers:R "$destination"
echo "Uploaded $destination. Marketplace release, signing, and compatibility bounds remain manual."
