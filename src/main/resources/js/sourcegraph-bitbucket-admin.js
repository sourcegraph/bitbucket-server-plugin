AJS.toInit(async () => {
    const restURL = AJS.contextPath() + '/rest/sourcegraph-admin/1.0/'
    const adminForm = document.getElementById('admin')
    const submitBtn = document.getElementById('submit')
    const messageContainer = document.getElementById('message')
    const urlInput = document.getElementById('url')
    const errorClasses = ['aui-message-error', 'error']
    const successClasses = ['aui-message-success', 'success']

    // Fetch Sourcegraph URL value on page load.
    const response = await fetch(restURL)
    if (!response.ok) {
        messageContainer.classList.add(...errorClasses)
        messageContainer.textContent = `Error fetching the Sourcegraph URL: ${response.status} ${response.statusText}`
        messageContainer.classList.remove('hidden')
        return
    }

    const { url } = await response.json()
    urlInput.value = url || ''

    // Update Sourcegraph URL when the form is submitted.
    adminForm.addEventListener('submit', async e => {
        e.preventDefault()
        submitBtn.classList.add('disabled')
        messageContainer.classList.remove(...errorClasses, ...successClasses)
        messageContainer.classList.add('hidden')

        const response = await fetch(restURL, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                url: document.getElementById('url').value,
            }),
        })
        submitBtn.classList.remove('disabled')

        // Display a success/error message
        if (!response.ok) {
            messageContainer.classList.add(...errorClasses)
            messageContainer.textContent = `Error saving the Sourcegraph URL: received status code ${response.status}.`
        } else {
            messageContainer.classList.add(...successClasses)
            messageContainer.textContent = `Sourcegraph URL successfully saved.`
        }
        messageContainer.classList.remove('hidden')
    })
})
