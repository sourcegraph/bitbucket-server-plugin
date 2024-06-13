# Local Development

Due some older dependencies, you'll need develop using Java 8. To install on MacOS, do the following:

```
brew install --cask temurin@8
```

In order to develop locally you will need to install the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/downloads/), note that it supports installation via Homebrew on MacOS:

```bash
brew tap atlassian/tap
brew install atlassian/tap/atlassian-plugin-sdk
```

Don't forget to set you `JAVA_HOME` environment variable for Java 8. On MacOS you can see your Java installations with the following command:

```aidl
/usr/libexec/java_home -V
```

If using IntelliJ you'll need to configure it to use the Atlassian version of Maven. Find your settings.xml file:

```text
atlas-version
```

Look for the line ending in `settings.xml`, copy the directory.

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

To release a version of the plugin, run `./scripts/release.sh <CURRENT VERSION> <NEW VERSION>`. This script uploads a new version of the plugin jar to google cloud. There are two requirements for this script to work being: (1) atlassian sdk installed and (2) gcloud cli set up with the sourcegraph-dev project.

```
./scripts/release.sh 1.1.0 1.2.0
```

The new version should be the one specified in `pom.xml`.
