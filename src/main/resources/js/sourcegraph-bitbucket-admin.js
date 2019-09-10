AJS.toInit(async () => {
    const restURL = AJS.contextPath() + '/rest/sourcegraph-admin/1.0/'
    const adminForm = document.getElementById('admin')
    const submitBtn = document.getElementById('submit')
    const messageContainer = document.getElementById('message')
    const urlInput = document.getElementById('url')
    const errorClasses = ['aui-message-error', 'error']
    const successClasses = ['aui-message-success', 'success']

    /**
     * Displays an error or success message in the `messageContainer` element.
     *
     * @param {string} type The type of the message as a string: `'error' | 'success'`.
     * @param {string} message The message to display.
     */
    const showMessage = (type, message) => {
        messageContainer.classList.add(...(type === 'error' ? errorClasses : successClasses))
        messageContainer.textContent = message
        messageContainer.classList.remove('hidden')
    }

    // Fetch Sourcegraph URL value on page load.
    try {
      const response = await fetch(restURL)
      if (!response.ok) {
          showMessage('error', `Error fetching the Sourcegraph URL: ${response.status} ${response.statusText}`)
      } else {
        const { url } = await response.json()
        urlInput.value = url || ''
      }
    } finally {
      urlInput.removeAttribute('disabled')
      urlInput.focus()
    }

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
            showMessage('error', `Error saving the Sourcegraph URL: ${response.status} ${response.statusText}.`)
        } else {
            showMessage('success', `Sourcegraph URL successfully saved.`)
        }
    })
})
