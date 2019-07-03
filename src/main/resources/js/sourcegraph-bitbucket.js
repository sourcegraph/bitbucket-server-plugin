AJS.toInit(async () => {
    // Fetch Sourcegraph URL on page load
    const response = await fetch(AJS.contextPath() + '/rest/sourcegraph-admin/1.0/')
    if (!response.ok) {
        console.error(`Error fetching Sourcegraph URL: ${response.status}`)
    }
    const { url } = await response.json()
    if (!url) {
        console.log(
            `No Sourcegraph URL is set. To set a Sourcegraph URL, log in as a site admin and navigate to ${AJS.contextPath()}/plugins/servlet/sourcegraph.`
        )
        return
    }
    // If a Sourcegraph URL is set,
    // inject a <script> tag to fetch the main JS bundle
    // from the Sourcegraph instance
    window.SOURCEGRAPH_URL = url
    window.localStorage.SOURCEGRAPH_URL = url
    window.SOURCEGRAPH_INTEGRATION = 'bitbucket-integration'
    var script = document.createElement('script')
    script.type = 'text/javascript'
    script.defer = true
    script.src = url + '/.assets/extension/scripts/integration.bundle.js'
    document.getElementsByTagName('head')[0].appendChild(script)
})
