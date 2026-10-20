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
is `target/sourcegraph-bitbucket-4.0.0.jar`. Packaging alone does not prove OSGi
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
amp orb service start bitbucket-test --command "$PWD/scripts/run-bitbucket.sh 10.5.1 $PWD/target/sourcegraph-bitbucket-4.0.0.jar 7990 /tmp/bitbucket-test"
uv run scripts/test-runtime.py --url http://localhost:7990
amp orb service stop bitbucket-test
sudo rm -rf /tmp/bitbucket-test # only the disposable home created above
```

Outside an orb, run the fixture command in one terminal and the test in another.
The test waits for `RUNNING`, authenticates via browser-session login and bearer
PATs, checks config authorization and exact Roaring bitmap/native API parity for
read/write/admin access, and creates a real Git push and PR comment to verify
webhook payloads and independently computed HMAC signatures. It also checks
anonymous/non-admin denials, identity isolation, error boundaries, and deletion.
Run once per fresh home; it intentionally leaves its test users and repositories
in that disposable home. `--keep-hook` retains the webhook for restart checks.

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

The October 2026 verification used a matching Sourcegraph `sg start` stack with
a normal development license, freshly built frontend/Gitserver and integration
bundle, and real SCIP indexes against Bitbucket 9.6.5 and 10.5.1. It exercised
source/history and unified/split PR hovers, unequal diff sides, native text
selection, definition clicks, populated reference panels and result navigation.
Private Bitbucket 10 repositories cloned through the native connector with Basic
auth disabled; wrong bearer credentials failed while valid PATs succeeded.

That testing found two additional bugs: PR file switching kept the previous
file's hover state, and position-only reference URLs produced empty Explore
results because the API requires the complete symbol range. Both fixes were
verified on the real stack, including deleted-file base revisions and head-file
revisions after switching in both directions. The reference scenario changed
from zero results to three precise references without changing its index or data.
The two fixture indexes share exported package/symbol identities, so exported
definitions may resolve to the equivalent fixture on the other Bitbucket host;
this does not prove same-repository attribution. Local-symbol navigation and
reference request repository/path/revision were checked independently.

The Sourcegraph release must include the [shared hover fixes](https://github.com/sourcegraph/sourcegraph/pull/17288),
[Bitbucket adapter and PR lifetime fixes](https://github.com/sourcegraph/sourcegraph/pull/17285),
[Git bearer-token authentication](https://github.com/sourcegraph/sourcegraph/pull/17287),
and [reference URL resolution](https://github.com/sourcegraph/sourcegraph/pull/17302).
Track integration and release selection in [CU-3522](https://linear.app/sourcegraph/issue/CU-3522/ship-bitbucket-data-center-10-compatibility).
These are development-stack results, not a released Sourcegraph version. The
earlier mixed-version test against Sourcegraph 6.12.0 is not a supported upgrade:
that server does not serve current `/r/` routes. Do not retrofit only the bundle
onto an older server. Older native Git HTTP also needs the bearer-token fix or
the connector's supported SSH configuration when Bitbucket disables Basic auth.

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
3. Leave existing `latest.jar` and the 3.0.1 Marketplace version intact for
   Bitbucket 9. If archiving `3.0.1.jar`, copy the verified existing artifact;
   never rebuild it from the Jakarta branch. Do not extend its bounds to 10.x.
4. Submit a new **4.0.0** Data Center Marketplace version for app key
   `com.sourcegraph.plugins.sourcegraph-bitbucket`, with a new unique build number
   greater than 2010051. Set the Bitbucket minimum to the earliest verified
   version (10.0.2) and maximum to the latest verified version (10.5.1); refresh
   Atlassian's feeds before submission. Do not claim untested 10.0.0/10.0.1.
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
