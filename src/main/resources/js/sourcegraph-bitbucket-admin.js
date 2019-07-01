AJS.toInit(() => {
  const url = AJS.contextPath() + '/rest/sourcegraph-admin/1.0/'

  // Fetch Sourcegraph URL value on page load.
  AJS.$.ajax({
    url,
    dataType: 'json'
  }).done(({ url }) => {
    document.getElementById('url').value = url || ''
  })

  // Update Sourcegraph URL when form is submitted.
  AJS.$('#admin').submit(e => {
    e.preventDefault()
    const data = JSON.stringify({
      url: document.getElementById('url').value
    })
    AJS.$.ajax({
      url: AJS.contextPath() + "/rest/sourcegraph-admin/1.0/",
      type: "PUT",
      contentType: "application/json",
      data,
      processData: false
    });
  })
})
