#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["requests==2.32.5", "pyroaring==1.0.3"]
# ///
"""Public API regression tests against a disposable run-bitbucket.sh fixture.

Creates local test users/repos/tokens. Never run against a shared installation.
The fixture home must be discarded after the test, including after failures.
"""
import argparse
import hashlib
import hmac
import html
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

import requests
from pyroaring import BitMap

API = "/rest/sourcegraph-admin/1.0"


def request(session, method, path, status=200, **kwargs):
    response = session.request(method, BASE + path, timeout=60, **kwargs)
    assert response.status_code == status, f"{method} {path}: expected {status}, got {response.status_code}"
    return response


def login(username, password):
    session = requests.Session()
    session.headers["X-Atlassian-Token"] = "no-check"
    request(session, "POST", "/rest/tsv/latest/authenticate", json={"username": username, "password": password})
    if username == "admin":
        page = request(session, "GET", "/websudo?next=/").text
        csrf = html.unescape(re.search(r"xsrfTokenValue: '([^']+)'", page)[1])
        request(session, "POST", "/websudo?next=/", data={"j_username": username, "j_password": password, "atl_token": csrf})
    return session


def token_session(session, username):
    token = request(session, "PUT", f"/rest/access-tokens/1.0/users/{username}", json={
        "name": "sourcegraph-runtime-test", "permissions": ["REPO_WRITE"]
    }).json()["token"]
    bearer = requests.Session()
    bearer.headers.update({"Authorization": "Bearer " + token, "X-Atlassian-Token": "no-check"})
    return bearer


def bitmap(session, path):
    response = request(session, "GET", API + path)
    result = set(BitMap.deserialize(response.content))
    assert int(response.headers["X-Debug-Count"]) == len(result)
    return result


def native_repositories(session, permission):
    result = set()
    start = 0
    while True:
        page = request(session, "GET", "/rest/api/latest/repos", params={
            "permission": "REPO_" + permission.upper(), "start": start
        }).json()
        result.update(repo["id"] for repo in page["values"])
        if page["isLastPage"]:
            return result
        start = page["nextPageStart"]


def run(args):
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        try:
            if requests.get(BASE + "/status", timeout=5).json().get("state") == "RUNNING":
                break
        except (requests.RequestException, ValueError):
            pass
        time.sleep(2)
    else:
        raise AssertionError("Bitbucket did not reach RUNNING")

    admin = login("admin", "admin")
    plugin = request(admin, "GET", "/rest/plugins/1.0/com.sourcegraph.plugins.sourcegraph-bitbucket-key").json()
    assert plugin["version"] == args.plugin_version, plugin["version"]
    assert plugin["enabled"] and plugin["modules"] and all(module["enabled"] for module in plugin["modules"])
    print(f'PASS plugin {plugin["version"]} enabled, all {len(plugin["modules"])} modules active', flush=True)
    bearer = token_session(admin, "admin")
    anonymous = requests.Session()
    request(bearer, "PUT", API + "/", status=204, json={"url": "https://sourcegraph.example.test"})
    assert request(bearer, "GET", API + "/").json() == {"url": "https://sourcegraph.example.test"}
    for path in ["/", "/webhook", "/permissions/repositories?permission=read", "/permissions/users?repository=PROJECT_1/rep_1&permission=read"]:
        request(anonymous, "GET", API + path, status=401)
    assert "Sourcegraph" in request(admin, "GET", "/plugins/servlet/sourcegraph").text
    print("PASS bearer config read/write, admin servlet, anonymous denial", flush=True)

    request(admin, "POST", "/rest/api/latest/projects", status=201, json={"key": "SGTEST", "name": "Sourcegraph runtime test"})
    repositories = []
    for name in ["allowed", "denied"]:
        repositories.append(request(admin, "POST", "/rest/api/latest/projects/SGTEST/repos", status=201,
                                    json={"name": name, "scmId": "git"}).json())
    allowed, denied = [repo["id"] for repo in repositories]
    users = {}
    for name, permission in [("sg-reader", "REPO_READ"), ("sg-writer", "REPO_WRITE"), ("sg-none", None)]:
        password = "fixture-only-password"
        request(admin, "POST", "/rest/api/latest/admin/users", status=204, params={
            "name": name, "password": password, "displayName": name,
            "emailAddress": name + "@example.test", "addToDefaultGroup": "false", "notify": "false"
        })
        request(admin, "PUT", "/rest/api/latest/admin/permissions/users", status=204,
                params={"name": name, "permission": "LICENSED_USER"})
        if permission:
            request(admin, "PUT", "/rest/api/latest/projects/SGTEST/repos/allowed/permissions/users", status=204,
                    params={"name": name, "permission": permission})
        identity = request(admin, "GET", f"/rest/api/latest/users/{name}").json()
        user = token_session(login(name, password), name)
        users[name] = identity["id"]
        request(user, "PUT", API + "/", status=401, json={"url": "https://wrong.example.test"})
        request(user, "GET", API + "/webhook", status=401)
        request(user, "GET", API + "/permissions/users?repository=SGTEST/allowed&permission=read", status=401)
        for level in ["read", "write", "admin"]:
            actual = bitmap(user, "/permissions/repositories?permission=" + level + "&user=admin")
            assert actual == native_repositories(user, level), (name, level, actual)
            expect_allowed = permission == "REPO_WRITE" and level in ["read", "write"] or permission == "REPO_READ" and level == "read"
            assert (allowed in actual) == expect_allowed, (name, level, actual)
            assert denied not in actual, (name, level, actual)
    for level, expected in [("read", {users["sg-reader"], users["sg-writer"]}), ("write", {users["sg-writer"]}), ("admin", set())]:
        actual = bitmap(bearer, "/permissions/users?repository=SGTEST/allowed&permission=" + level)
        assert actual & set(users.values()) == expected, (level, actual)
        assert 2 in actual  # fixture system administrator
    request(bearer, "GET", API + "/permissions/repositories?permission=invalid", status=422)
    request(bearer, "GET", API + "/permissions/users?repository=SGTEST/missing&permission=read", status=404)
    print("PASS exact native/bitmap ACL parity; reader/writer/no-access, impersonation isolation, 422/404", flush=True)

    events = queue.Queue()
    secret = "disposable-runtime-test-secret"

    class Sink(BaseHTTPRequestHandler):
        def do_POST(self):
            body = self.rfile.read(int(self.headers["Content-Length"]))
            events.put((self.headers["X-Hook-Name"], self.headers["X-Event-Key"], self.headers["X-Hub-Signature"], body))
            self.send_response(204)
            self.end_headers()

        def log_message(self, *_):
            pass

    sink = ThreadingHTTPServer((args.sink_bind, args.sink_port), Sink)
    thread = threading.Thread(target=sink.serve_forever, daemon=True)
    thread.start()
    try:
        # One global hook plus scoped hooks. "runtime-other" is scoped to a
        # repository in another project and must only see that repository.
        sourcegraph = ["repo:refs_changed", "repo:build_status", "pr:activity", "pr:participant:status"]
        hooks = {
            "runtime-test": ("global", ["repo:refs_changed", "repo:build_status", "pr:activity:comment"]),
            "runtime-project": ("project:Project 1", ["repo", "pr:activity"]),
            "runtime-repository": ("repository:PROJECT_1/rep_1", ["repo", "pr"]),
            "runtime-other": ("repository:SGTEST/allowed", sourcegraph),
        }
        if args.plugin_version.startswith("3."):
            # The published 3.x control predates the scope fix; check global delivery only.
            hooks = {"runtime-test": hooks["runtime-test"]}
        for name, (scope, keys) in hooks.items():
            request(bearer, "POST", API + "/webhook", status=204, json={
                "name": name, "scope": scope, "events": keys, "endpoint": args.sink_url, "secret": secret})
        listed = {hook["name"]: hook for hook in request(bearer, "GET", API + "/webhook").json()}
        assert {name: listed.get(name, {}).get("scope") for name in hooks} == {name: hook[0] for name, hook in hooks.items()}, listed
        # Avoid putting the token into git arguments, logs, or persistent config.
        env = dict(os.environ, GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="http.extraHeader",
                   GIT_CONFIG_VALUE_0="Authorization: " + bearer.headers["Authorization"])

        def push(project, slug, empty):
            """Pushes a commit on a new branch (and master first if empty); returns the branch head."""
            with tempfile.TemporaryDirectory() as checkout:
                def git(*command):
                    return subprocess.run(["git", "-C", checkout, *command], env=env, check=True, text=True,
                                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout.strip()

                git("clone", f"{BASE}/scm/{project.lower()}/{slug}.git", ".")
                git("config", "user.name", "Runtime test")
                git("config", "user.email", "runtime@example.test")
                if empty:
                    git("checkout", "-b", "master")
                    git("commit", "--allow-empty", "-m", "Initial commit")
                    git("push", "origin", "HEAD:refs/heads/master")
                git("checkout", "-b", "sourcegraph-runtime-test")
                Path(checkout, "sourcegraph-runtime.txt").write_text("A real push exercises the packaged event listener.\n")
                git("add", ".")
                git("commit", "-m", "Exercise Sourcegraph webhook delivery")
                git("push", "origin", "HEAD")
                return git("rev-parse", "HEAD")

        def exercise(project, slug, empty):
            commit = push(project, slug, empty)
            repo = f"/rest/api/latest/projects/{project}/repos/{slug}"
            request(bearer, "POST", f"{repo}/commits/{commit}/builds", status=204, json={
                "key": "sourcegraph-runtime-test", "state": "SUCCESSFUL", "url": "https://ci.example.test/1"})
            ref = {"repository": {"slug": slug, "project": {"key": project}}}
            pr = request(bearer, "POST", f"{repo}/pull-requests", status=201, json={
                "title": "Sourcegraph runtime test", "fromRef": dict(ref, id="refs/heads/sourcegraph-runtime-test"),
                "toRef": dict(ref, id="refs/heads/master")
            }).json()
            request(bearer, "POST", f'{repo}/pull-requests/{pr["id"]}/comments',
                    status=201, json={"text": "Exercise signed PR comment webhook"})
            return commit, pr["id"]

        rep_1, other = 1, allowed
        commits, prs = {}, {}
        for repo_id, project, slug, empty in [(rep_1, "PROJECT_1", "rep_1", False), (other, "SGTEST", "allowed", True)]:
            commit, prs[repo_id] = exercise(project, slug, empty)
            commits[commit] = repo_id

        def repository_of(key, payload):
            if key == "repo:refs_changed":
                return payload["repository"]["id"]
            if key == "repo:build_status":
                status = payload.get("status") or {}
                assert (status.get("state"), status.get("key"), status.get("url")) == (
                    "SUCCESSFUL", "sourcegraph-runtime-test", "https://ci.example.test/1"), payload
                return commits[payload["commit"]]
            assert "pullRequest" in payload, (key, sorted(payload))
            repo_id = payload["pullRequest"]["toRef"]["repository"]["id"]
            assert payload["pullRequest"]["id"] == prs[repo_id], payload["pullRequest"]["id"]
            return repo_id

        core = ["repo:refs_changed", "repo:build_status", "pr:activity:comment"]
        scoped = core + ["pr:activity:event"]  # PR opened
        required = {
            "runtime-test": {(key, repo) for key in core for repo in [rep_1, other]},
            "runtime-project": {(key, rep_1) for key in scoped},
            "runtime-repository": {(key, rep_1) for key in scoped + ["pr:event"]},
            "runtime-other": {(key, other) for key in scoped},
        }
        allowed_repos = {"runtime-test": {rep_1, other}, "runtime-project": {rep_1},
                         "runtime-repository": {rep_1}, "runtime-other": {other}}
        received = {name: set() for name in hooks}
        deadline = time.monotonic() + 60
        settle = None
        while time.monotonic() < (settle or deadline):
            try:
                name, key, signature, body = events.get(timeout=1)
            except queue.Empty:
                if settle is None and all(required[n] <= received[n] for n in hooks):
                    settle = time.monotonic() + 10  # wait for any wrongly scoped stragglers
                continue
            assert hmac.compare_digest(signature, "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest())
            if key == "ping":
                continue
            assert any(key == event or key.startswith(event + ":") for event in hooks[name][1]), (name, key)
            received[name].add((key, repository_of(key, json.loads(body))))
        names = {rep_1: "PROJECT_1/rep_1", other: "SGTEST/allowed"}
        for name in hooks:
            print(f"  {name} ({hooks[name][0]}): " + ", ".join(sorted(f"{k}@{names[r]}" for k, r in received[name])), flush=True)
        for name in hooks:
            assert required[name] <= received[name], (name, "missing", required[name] - received[name])
            assert {repo for _, repo in received[name]} <= allowed_repos[name], (name, "out of scope", received[name])
        print("PASS real git push, build status and PR comment webhooks: payload IDs, independent HMAC, "
              "global/project/repository scopes, no delivery outside scope", flush=True)
        if not args.keep_hook:
            for name in hooks:
                request(bearer, "DELETE", API + "/webhook", status=204, data={"name": name})
            assert request(bearer, "GET", API + "/webhook").json() == []
            print("PASS webhook deletion", flush=True)
    finally:
        sink.shutdown()
        sink.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True)
    parser.add_argument("--plugin-version", default="4.0.1")
    parser.add_argument("--sink-bind", default="0.0.0.0")
    parser.add_argument("--sink-port", type=int, default=8765)
    parser.add_argument("--sink-url", default="http://172.17.0.1:8765/")
    parser.add_argument("--keep-hook", action="store_true", help="Keep webhook for manual restart verification")
    args = parser.parse_args()
    BASE = args.url.rstrip("/")
    if urlparse(BASE).hostname not in {"localhost", "127.0.0.1"}:
        parser.error("Only disposable loopback fixtures are supported")
    run(args)
