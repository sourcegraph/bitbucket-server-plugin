AJS.toInit(() => {
    // Fetch Sourcegraph URL on page load
    AJS.$.ajax({
        url: AJS.contextPath() + '/rest/sourcegraph-admin/1.0/',
        dataType: 'json'
    }).done(({ url }) => {
        if (!url) {
            console.log(`No Sourcegraph URL is set. To set a Sourcegraph URL, log in as a site admin and navigate to ${AJS.contextPath()}/plugins/servlet/sourcegraph.`)
            return
        }
        console.log(url)
        // If a Sourcegraph URL is set,
        // inject a <script> tag to fetch the main JS bundle
        // from the Sourcegraph instance
        window.SOURCEGRAPH_URL = url
        window.localStorage.SOURCEGRAPH_URL = url
        window.SOURCEGRAPH_PHABRICATOR_EXTENSION = true
        var script = document.createElement('script')
        script.type = 'text/javascript'
        script.defer = true
        script.src = url + '/.assets/extension/scripts/integration.bundle.js'
        document.getElementsByTagName('head')[0].appendChild(script)
    })
})
