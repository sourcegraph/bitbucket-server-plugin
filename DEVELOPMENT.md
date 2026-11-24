# Local Development

Due some older dependencies, you'll need to develop using Java 17. To install on MacOS, do the following:

```sh
brew install --cask temurin@17
```

In order to develop locally you will need to install the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/downloads/), note that it supports installation via Homebrew on MacOS:

```sh
brew tap atlassian/tap
brew install atlassian/tap/atlassian-plugin-sdk
```

Don't forget to set your `JAVA_HOME` environment variable for Java 17. On MacOS you can see your Java installations with the following command:

```sh
/usr/libexec/java_home -V
```

If using IntelliJ you'll need to configure it to use the Atlassian version of Maven. Find your `settings.xml` file by running the Atlassian Plugin SDK version tool:

```sh
atlas-version
```

Look for the line ending in `settings.xml`, copy the directory.

This command should print out the directory path for you:
```sh
dirname $(atlas-version | grep 'settings\.xml$' | awk '{print $NF}')
```

In IntelliJ, navigate to Preferences -> Build, Execution, Deployment -> Build Tools -> Maven

Override `User Settings File` with the directory you copied above. 

The following commands are also useful for local development:

- `atlas-run` -- installs this plugin into the product and starts it on localhost (`--context-path /` will run it on [http://localhost:7990](http://localhost:7990) instead of [http://localhost:7990/bitbucket](http://localhost:7990/bitbucket))
- `atlas-debug` -- same as atlas-run, but allows a debugger to attach at port 5005
- `atlas-help` -- prints description for all commands in the SDK

See also the Atlassian Plugin SDK [documentation](https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK).

The default credentials are `admin/admin` for the `atlas-run` environment.

## Attaching a debugger

As mentioned above, running `atlas-debug` will run in debug mode. In order to attach a debugger using IntelliJ follow these steps:

1. Run -> Debug -> Edit Configuration
1. Click `+` and select `Remote`
1. The default settings should work, just name it for example `Bitbucket`
1. Run -> Debug, select the name you used above
1. The console should say that it has attached and you can add breakpoints etc 

# Releasing

The `3.x` maintenance branch targets Bitbucket Data Center 9.0.0–9.6.5 with Java 17, Platform 7 and `javax`. The 4.x `master` branch targets Bitbucket 10 with Java 21 and Jakarta; do not merge 3.x changes into 4.x or build a Bitbucket 9 patch from `master`.

For a 3.x security patch, build and test the JAR from the 3.x maintenance branch. Record its SHA-256 and test a fresh Bitbucket 9 installation and a 3.0.1 upgrade, including UPM module activation after restart. Submit that exact tested JAR as a new **Paid via Vendor** Marketplace version with Bitbucket 9 compatibility bounds; do not rebuild it after validation. A submitted version is not released until Marketplace approves it and the public download is available. Download the public artifact and signature, verify the bytes against the tested JAR and run the published artifact on Bitbucket 9 before updating the security finding. Plugin 3.0.2 is Marketplace build 2010081; its public JAR is at <https://marketplace.atlassian.com/download/apps/1231975/version/2010081>.

Plugin 3.0.3 is Marketplace build 2010101, published November 24, 2026 for 9.0.0–9.6.5; its public JAR is at <https://marketplace.atlassian.com/download/apps/1231975/version/2010101>. It backports the 4.0.1 webhook fixes from `master` and was built from tag `v3.0.3` with Java 17 (SHA-256 `0bea584e943620cd43cbd0e41a7ba202485d86e8879c97ca96a325c5af8313fb`). `master`'s runtime test, with scoped-hook checks enabled and a `j_atl_security_check` login fallback for 9.0/9.2, passed on 9.0.0, 9.2.0 and 9.6.5 and failed on 3.0.2. A UPM upgrade from 3.0.2 kept config and webhooks, which then delivered in scope across a restart. The public JAR is byte-identical, its Marketplace signature verifies, and it passed the suite on a fresh 9.6.5 install. The create-version form copies compatibility bounds from the latest version (4.x) and caps the release summary at 80 characters.

Do not run `scripts/release.sh` for a 3.x Marketplace-only patch: it rebuilds the JAR and replaces GCS `latest.jar`. GCS has not been updated for 3.0.3. After the separately approved GCS release, `latest.jar` and `3.0.2.jar` serve the verified 3.0.2 Marketplace bytes; `3.0.1.jar` preserves the previous release. The versioned `4.0.0.jar` serves the Bitbucket 10 release, but `latest.jar` remains on the Bitbucket 9-compatible line. Marketplace publication does not update GCS automatically.
