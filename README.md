# Sourcegraph for Bitbucket Server

The Sourcegraph plugin for Bitbucket Server communicates with your Sourcegraph instance to add code intelligence to your Bitbucket Server code views and pull requests.

## Installation and Usage

### Prerequisites

1. You must have a self-hosted Sourcegraph instance set up, v3.6 or higher.
2. Your Sourcegraph instance should have a Bitbucket Server [external service](https://docs.sourcegraph.com/admin/external_service/bitbucket_server) configured.
3. The `corsOrigin` property should be set in [site configuration](https://docs.sourcegraph.com/admin/config/site_config) to include the URL of your Bitbucket Server instance. Example value:

```json
    "corsOrigin": "https://bitbucket.internal.org"
```

### Configuration

After installing the Sourcegraph for Bitbucket Server, you should configure it to point to your Sourcegraph instance.

1. On Bitbucket, go to the **Administration** page
2. Find the **Sourcegraph** entry under **Add-ons**:

![Add-ons](img/add-ons.png)

3. On the **Sourcegraph Settings** page, set the Sourcegraph URL to the URL of your self-hosted Sourcegraph instance:

![Sourcegraph settings](img/sourcegraph-settings.png)

### Usage

Once configured, Sourcegraph for Bitbucket Server will add code intelligence hovers to code views and pull requests for all users that are logged in to your self-hosted Sourcegraph instance. It will also add links to view repositories, files and diffs on Sourcegraph.

![Code intelligence](img/code-intelligence.png)

Additionally, activated [Sourcegraph extensions](https://docs.sourcegraph.com/extensions) will be able to add information to Bitbucket server code views and pull requests, such as test coverage data or trace/log information.

If a user is not logged in to Sourcegraph, they will still see the "View Repository on Sourcegraph" links, but code intelligence hovers as well as any data contributed by Sourcegraph extensions will not be displayed.

## Local Development

-   `atlas-run` -- installs this plugin into the product and starts it on localhost
-   `atlas-debug` -- same as atlas-run, but allows a debugger to attach at port 5005
-   `atlas-help` -- prints description for all commands in the SDK

See also the Atlassian Plugin SDK [documentation](https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK).
