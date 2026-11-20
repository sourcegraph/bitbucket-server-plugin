# Development and compatibility

The 4.x line targets Bitbucket Data Center 10 (Java 21, Platform 8, Jakarta EE 10).
The published 3.0.1 line targets Bitbucket 9 (Java 17, Platform 7, `javax`). These
are different artifacts, not one JAR with wider Marketplace compatibility bounds.

Install a Java 21 JDK and Maven 3.8.7 or later, then run from the repository root:

```sh
mvn -B -ntp clean verify
```

`.mvn/settings.xml` supplies Atlassian and Central repositories for dependencies
and Maven plugins; the Atlassian SDK is not required to package the app. The JAR
is `target/sourcegraph-bitbucket-4.0.1.jar`. Packaging alone does not prove OSGi
activation: there are no JVM unit tests; the public-interface runtime suite below
is the regression check for this migration.

In an Amp orb, `.agents/setup` installs Java/Maven and warms the dependency cache.
It is idempotent. Docker is needed only for runtime tests. Start the daemon as a
supervised service if necessary:

```sh
amp orb service start docker --command 'sudo dockerd --host=unix:///var/run/docker.sock'
```

## Runtime regression tests

`scripts/run-bitbucket.sh` downloads Atlassian's matching development home and
runs the official image in the foreground, bound to loopback. The home must be a
new, disposable directory. It contains development credentials and a license;
never commit it, upload raw logs, or use it as production data.

```sh
amp orb service start bitbucket-test --command "$PWD/scripts/run-bitbucket.sh 10.5.1 $PWD/target/sourcegraph-bitbucket-4.0.1.jar 7990 /tmp/bitbucket-test"
uv run scripts/test-runtime.py --url http://localhost:7990
amp orb service stop bitbucket-test
sudo rm -rf /tmp/bitbucket-test # only the disposable home created above
```

Outside an orb, run the fixture command in one terminal and the test in another.
The test waits for `RUNNING`, authenticates via browser-session login and bearer
PATs, checks config authorization and exact Roaring bitmap/native API parity for
read/write/admin access, and creates real Git pushes, build statuses and PR
comments in two projects to verify webhook payloads, independently computed HMAC
signatures, and global, project and repository scopes (including no delivery to a
webhook scoped to another repository). It also checks
anonymous/non-admin denials, identity isolation, error boundaries, and deletion.
Run once per fresh home; it intentionally leaves its test users and repositories
in that disposable home. `--keep-hook` retains the webhooks for restart checks.

The webhook receiver defaults to Docker's Linux bridge (`172.17.0.1:8765`). Pass
`--sink-port` and `--sink-url` for another bridge or concurrent runs. It listens on
all local interfaces so the container can reach it; do not expose that test port.

Buildkite packages on Java 21 and runs this suite on 10.0.2, 10.1.5, 10.2.8,
10.3.2, 10.4.3, and 10.5.1. Agents need Docker access, Python 3, and tar.
CI copies files through Docker's API rather than bind-mounting the agent's
filesystem, so sidecar daemons work too. A pinned Python/uv test container shares
the Bitbucket container's network for REST, Git, and webhook callbacks; no ports
are exposed. `.buildkite/test-runtime.py VERSION` owns this finite lifecycle and
removes both containers and anonymous volumes even when assertions fail.

For the Bitbucket 9 control, use the unchanged published 3.0.1 JAR on 9.6.5;
its SHA-256 is `5ed6887a33b0bacfe37943b50540f653dbc0c8846cffc4af3c0cbe93a41701de`.
Pass `--plugin-version 3.0.1` to the runtime test. Do not try to run the Java
21/Jakarta JAR on Bitbucket 9.

Development fixtures use H2 and may persist Basic authentication as enabled even
on Bitbucket 10. The test uses PATs, but for a production-like run also disable
Basic auth in **Administration > Authentication methods**, confirm Basic returns
403, and repeat the API checks. The runner explicitly keeps UPM signature
verification enabled; copying a JAR into the initial home is not a signed-install
test. Test upload through UPM separately. Single-node H2 tests do not certify
cluster behavior, every supported database, or production scale.

## Browser integration is a separate release

The JAR loads `/.assets/extension/scripts/integration.bundle.js` from the configured
Sourcegraph instance. The Bitbucket adapter lives in `sourcegraph/sourcegraph`,
under `client/browser/src/shared/code-hosts/bitbucket`. A new JAR cannot update
that bundle. Verify source and PR views using a Sourcegraph release containing
the adapter changes; do not infer the customer's bundle from this repository or
from Sourcegraph master. Fixture/DOM tests alone do not prove code intelligence
against a real Sourcegraph backend.

**Sourcegraph 8.2.0 or later is required for plugin 4.x.** It includes the
[shared hover fixes](https://github.com/sourcegraph/sourcegraph/pull/17288),
[Bitbucket adapter and PR lifetime fixes](https://github.com/sourcegraph/sourcegraph/pull/17285),
[Git bearer-token authentication](https://github.com/sourcegraph/sourcegraph/pull/17287),
and [reference URL resolution](https://github.com/sourcegraph/sourcegraph/pull/17302).
Track integration and release selection in [CU-3522](https://linear.app/sourcegraph/issue/CU-3522/ship-bitbucket-data-center-10-compatibility).
The earlier mixed-version test against Sourcegraph 6.12.0 is not a supported upgrade:
that server does not serve current `/r/` routes. Do not retrofit only the bundle
onto an older server. Older native Git HTTP also needs the bearer-token fix or
the connector's supported SSH configuration when Bitbucket disables Basic auth.

## Published 4.0.1 release and validation

Published on November 20, 2026 as Marketplace build **2010091**, Paid via Vendor,
for Bitbucket Data Center **10.0.2–10.5.2**. It fixes project- and repository-scoped
webhooks, `repo:build_status` payloads without `status`, `pr:event` for requested
actions, and the listed repository scope format.

- [Marketplace version](https://marketplace.atlassian.com/apps/1231975/sourcegraph-for-bitbucket/version-history#b2010091)
- [Published JAR](https://marketplace.atlassian.com/download/apps/1231975/version/2010091)
- Build source: tag `v4.0.1`, [`ea3ed7f`](https://github.com/sourcegraph/bitbucket-server-plugin/commit/ea3ed7f74501964d5b3017ea9a0dc67abb878be6), Java 21, `mvn -B -ntp clean verify`.
- JAR SHA-256: `689a2e7842252d3da99e4be8eec84428f9ecac2438893a3923bfc54fbf6ad55f` (3,910,841 bytes).

The public download is byte-identical to the tested candidate. Before publication
the candidate passed the runtime suite on 10.0.2, 10.1.5, 10.2.8, 10.2.9, 10.3.2,
10.4.3, 10.5.1 and 10.5.2. After publication, on 10.5.2 with only the Atlassian CA
bundle v2 trusted, UPM rejected unsigned and dev-signed uploads and accepted the
published JAR with its Marketplace `signature.json` as an upgrade over published
4.0.0. Config, webhook IDs and scopes survived, webhooks created on 4.0.0 delivered
within their scope, and everything persisted after a restart. The Marketplace v2
REST API now returns 410, so check version metadata in the vendor UI.

## Published 4.0.0 release and validation

Published on November 19, 2026 as Marketplace build **2010071**, Paid via Vendor,
for Bitbucket Data Center **10.0.2–10.5.2**:

`master` is the Bitbucket 10 / Java 21 / Jakarta line. Maintain Bitbucket 9 /
Java 17 / `javax` patches on the separate
[`3.x` branch](https://github.com/sourcegraph/bitbucket-server-plugin/tree/3.x).
Its published 3.0.2 release fixes CVE-2026-102554; GCS `latest.jar` and
`3.0.2.jar` serve that release, while `3.0.1.jar` archives the previous one.

- [Marketplace version](https://marketplace.atlassian.com/apps/1231975/sourcegraph-for-bitbucket/version-history#b2010071)
- [Published JAR](https://marketplace.atlassian.com/download/apps/1231975/version/2010071)
- Build source: [`667c44b436d4d59af47adcd897304acac24abc10`](https://github.com/sourcegraph/bitbucket-server-plugin/commit/667c44b436d4d59af47adcd897304acac24abc10), Java 21, `mvn -B -ntp clean verify`.
- JAR SHA-256: `c97c7ba78eb735519d6ddbb373f41316f43f313d6715cbcc8710cef344567f74` (3,910,459 bytes).

The public download is byte-identical to the tested candidate; it was not rebuilt
for post-publication testing. On November 19, the published JAR passed:

- **176/176 fresh-install checks** across 10.0.2, 10.1.5, 10.2.8, 10.2.9,
  10.3.2, 10.4.3, 10.5.1 and 10.5.2. UPM accepted the genuine Marketplace
  signature with Atlassian CA trust, rejected missing/invalid signatures, and
  activated all eight modules. ACL/webhook checks and restart persistence passed
  with Basic authentication blocked. Marketplace-URL installation was also
  exercised on 10.2.9 and 10.5.2.
- **Two upgrade scenarios, 16 checks each:** 9.6.5 with public 3.0.1 to 10.2.9
  and 10.5.2 with published 4.0.0. Bitbucket disables user-installed apps during
  the major upgrade; the replacement plugin needed an explicit **Enable** in
  Manage apps. Configuration and existing webhooks survived, including real push
  delivery with valid HMAC and persistence after restart. Fresh installs enabled
  automatically.
- **114/114 Chromium code-intelligence checks** against released Sourcegraph
  8.2.0 on 10.2.9, 10.5.2 and the 9.6.5/3.0.1 control. Coverage included source,
  history, unified/split PRs with unequal line numbers, modified/deleted/added
  file switches, same-repository definitions and reference navigation. Distinct
  fixture package identities prevented cross-host symbol attribution. Private
  bearer-token cloning also passed on both Bitbucket 10 integration fixtures
  with Basic blocked; incorrect credentials failed.
- **16/16 Bitbucket 9 control checks** on freshly installed public 3.0.1. At
  test time, Marketplace 3.0.1 and GCS `latest.jar` matched the checksum above
  in the runtime-test section; `latest.jar` now serves 3.0.2. UPM 7.1.9 on
  this control does not enforce signatures.

These were single-node H2 Docker fixtures, with simulated major-version upgrades,
not cluster, production-database or performance certification. Sourcegraph ran
unlicensed, with private repositories tested one at a time; licensed permission
sync and Sourcegraph-side connector webhooks were not exercised. UPM installs
used REST paths, not the Find apps UI. Browser coverage was Chromium only.
Two unchanged browser limitations also occur on the Bitbucket 9 control: an open
tooltip can remain after changing unified/split mode until the next hover, and
single-commit pages have no hovers.

## Release without breaking Bitbucket 9

Repository pushes are not Marketplace releases or deployments. The historical
release script only rotated GCS `latest.jar`; it never updated the POM, tagged a
commit, published Marketplace metadata, or set compatibility bounds.

Before publication, obtain explicit release authorization and confirm access to
the Marketplace listing and (if used) the GCS bucket. Then:

1. Build and runtime-test the candidate from the release commit. Keep the JAR and
   its SHA-256; do not rebuild between verification, signing, and publication.
2. Run `./scripts/release.sh 4.0.0` to check the artifact version and checksum. This
   is local-only. `--upload` explicitly uploads an immutable `4.0.0.jar` to GCS
   using a generation precondition; it refuses to overwrite existing bytes.
3. Leave `latest.jar` on the Bitbucket 9-compatible 3.x line and keep the
   3.0.1 Marketplace version and GCS `3.0.1.jar` archive intact. Never rebuild
   a 3.x JAR from the Jakarta branch or extend its bounds to 10.x.
4. Submit a new Data Center Marketplace version for app key
   `com.sourcegraph.plugins.sourcegraph-bitbucket`, with a new unique build number
   greater than the latest existing build (4.0.1 uses 2010091; the form pre-fills the next one). Set the Bitbucket
   minimum to the earliest verified version (10.0.2) and maximum to the latest
   verified version (10.5.2 for 4.0.1); refresh
   Atlassian's feeds before submission. Do not claim untested 10.0.0/10.0.1.
   The form copies bounds and release text from the last submitted version (3.x
   or 4.x), and the Make public wizard defaults the payment model to Free; set
   Paid via vendor.
5. Marketplace signs distributed artifacts. For direct upload, use a separately
   approved vendor signing identity and distribute the detached signature and
   certificate via a trusted channel. The customer must configure UPM's trust
   store. Do not tell customers to disable signature verification.
6. Download the published artifact, compare its digest, check Marketplace bounds,
   install through UPM with signature verification enabled, and record the
   Sourcegraph release containing the frontend fix. Tagging and publishing are
   explicit release actions, not consequences of packaging or a master push.

For a disposable signing test, generate an Ed25519 key/certificate with codeSigning
usage, place the public certificate in `<Bitbucket shared home>/upmconfig/truststore`
(owned by a different user and not writable by Bitbucket), and sign the exact JAR
using `openssl pkeyutl -sign -rawin`. Base64-encode the signature. UPM's multipart
upload takes `plugin=@app.jar` and a `signature` field containing JSON
`{"signature":"<base64>"}`. Authenticate with a WebSudo-confirmed admin session,
obtain `upm-token` from `GET /rest/plugins/1.0/`, then upload. Parse the textarea-wrapped
response and follow the asynchronous task to completion; also verify plugin and
module activation. Check rejection of absent/wrong signatures as negative controls.
Test keys are not release credentials.

References: [Bitbucket API changelog](https://developer.atlassian.com/server/bitbucket/reference/api-changelog/),
[UPM signing configuration](https://confluence.atlassian.com/upm/configuring-upm-app-signature-check-1489470544.html),
[custom signatures](https://confluence.atlassian.com/upm/generating-app-signature-and-verification-certificate-using-openssl-1489470549.html).
