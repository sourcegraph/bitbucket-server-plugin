AJS.toInit(() => {
  const sourcegraphURL = 'http://localhost:3080'
  window.SOURCEGRAPH_URL = sourcegraphURL
  window.localStorage.SOURCEGRAPH_URL = sourcegraphURL
  window.SOURCEGRAPH_PHABRICATOR_EXTENSION = true;
  var script = document.createElement('script');
  script.type = 'text/javascript';
  script.defer = true;
  script.src =
    sourcegraphURL + '/.assets/extension/scripts/phabricator.bundle.js';
  document.getElementsByTagName('head')[0].appendChild(script);
})
