# Sourcegraph for Bitbucket

Use Java 21 and `mvn -B -ntp clean verify` from the repository root. Maven settings
are checked in; the Atlassian SDK is optional. `.agents/setup` prepares Amp orbs.

The 4.x JAR supports Bitbucket 10 / Jakarta. Bitbucket 9 needs the unchanged 3.x
artifact; never replace GCS `latest.jar` with 4.x or widen 3.x Marketplace bounds.
Read DEVELOPMENT.md before runtime testing or releasing. `scripts/test-runtime.py`
mutates only disposable local fixtures, not shared Bitbucket instances. Fixtures
contain credentials/license data; keep homes, keys and raw logs out of Git.

Packaging is not activation. For platform/API changes, run the runtime matrix and
check UPM module states. For UI changes, test the bundle served by Sourcegraph;
the adapter is in the separate sourcegraph/sourcegraph repository. Publishing a
Marketplace/GCS version and deploying Sourcegraph require release authorization.
