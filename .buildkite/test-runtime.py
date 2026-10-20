#!/usr/bin/env python3
"""Run an isolated fixture even when Docker is a remote/sidecar daemon."""
import glob
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

version = sys.argv[1]
jar, = glob.glob("target/sourcegraph-bitbucket-*.jar")
containers = []
with tempfile.TemporaryDirectory(prefix="sourcegraph-bitbucket-") as directory:
    archive = Path(directory, "fixture.zip")
    urllib.request.urlretrieve(
        f"https://maven.artifacts.atlassian.com/com/atlassian/bitbucket/server/bitbucket-it-resources/{version}/bitbucket-it-resources-{version}.zip",
        archive)
    with zipfile.ZipFile(archive) as fixture:
        fixture.extractall(directory)

    def create(*args):
        container = subprocess.check_output(["docker", "create", *args], text=True).strip()
        containers.append(container)
        return container

    def docker(*args):
        subprocess.run(["docker", *args], check=True)

    try:
        home = "/var/atlassian/application-data/bitbucket"
        bitbucket = create(
            "-e", "SEARCH_ENABLED=false", "-e", "JVM_MINIMUM_MEMORY=512m", "-e", "JVM_MAXIMUM_MEMORY=1536m",
            "-e", "JVM_SUPPORT_RECOMMENDED_ARGS=-Datlassian.dev.mode=true -Datlassian.upm.signature.check.disabled=false",
            f"atlassian/bitbucket:{version}", "bash", "-c",
            f"chown -R 2003:2003 {home} && exec /entrypoint.py --log=INFO")
        fixture_home = Path(directory, "generated-resources/bitbucket-home")
        installed = fixture_home / "shared/plugins/installed-plugins"
        installed.mkdir(parents=True, exist_ok=True)
        (installed / "sourcegraph.jar").write_bytes(Path(jar).read_bytes())
        docker("cp", str(fixture_home) + "/.", f"{bitbucket}:{home}")
        docker("start", bitbucket)
        # Sharing only this container's network makes localhost work for both
        # REST/Git and webhook callbacks, without publishing ports to the host.
        client = create("--network", f"container:{bitbucket}",
                        "ghcr.io/astral-sh/uv:0.9.4-python3.11-bookworm",
                        "uv", "run", "/test-runtime.py", "--url", "http://localhost:7990",
                        "--sink-url", "http://localhost:8765/")
        docker("cp", "scripts/test-runtime.py", f"{client}:/test-runtime.py")
        docker("start", "-a", client)
        code = subprocess.check_output(["docker", "inspect", "--format", "{{.State.ExitCode}}", client], text=True).strip()
        assert code == "0", f"Runtime test exited {code}"
    finally:
        # Remove anonymous home volumes too. Never publish raw fixture logs.
        for container in reversed(containers):
            subprocess.run(["docker", "rm", "-f", "-v", container], check=True, stdout=subprocess.DEVNULL)
