#!/usr/bin/env python3
"""Own the fixture process for one finite CI test, and clean it on failure too."""
import glob
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile

version = sys.argv[1]
jar, = glob.glob("target/sourcegraph-bitbucket-*.jar")
# A job-local port avoids collisions on hosts with several Buildkite agents.
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
with socket.socket() as sock:
    sock.bind(("0.0.0.0", 0))
    sink_port = sock.getsockname()[1]

docker = ["docker"]
if subprocess.run(docker + ["info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    docker = ["sudo", "docker"]

with tempfile.TemporaryDirectory(prefix="sourcegraph-bitbucket-") as directory:
    home = Path(directory, "fixture")
    container = f"sourcegraph-bitbucket-{port}"
    process = subprocess.Popen(["scripts/run-bitbucket.sh", version, jar, str(port), str(home)],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        subprocess.run(["uv", "run", "scripts/test-runtime.py", "--url", f"http://localhost:{port}",
                        "--sink-port", str(sink_port), "--sink-url", f"http://172.17.0.1:{sink_port}/"], check=True)
    finally:
        # Do not upload raw fixture logs: upstream logs can contain credentials.
        subprocess.run(docker + ["stop", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        process.terminate()
        process.wait(timeout=30)
        if home.exists():
            subprocess.run(["sudo", "chown", "-R", f"{os.getuid()}:{os.getgid()}", str(home)], check=True)
