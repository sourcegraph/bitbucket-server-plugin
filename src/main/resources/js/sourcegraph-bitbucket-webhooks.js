AJS.toInit(async () => {
    AJS.$('.webhook-error-tooltip').tooltip()

    const restURL = AJS.contextPath() + '/rest/sourcegraph-admin/1.0/webhook'
    const form = document.getElementById('webhook')
    const submitBtn = document.getElementById('webhook-submit')
    const messageContainer = document.getElementById('webhook-message')
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

    form.addEventListener('submit', async e => {
        e.preventDefault()
        submitBtn.classList.add('disabled')
        messageContainer.classList.remove(...errorClasses, ...successClasses)
        messageContainer.classList.add('hidden')
        const parsedEvents = form.elements.events.value.trim() ? form.elements.events.value.split(':') : []
        const response = await fetch(restURL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: form.elements.name.value.trim(),
                scope: form.elements.scope.value.trim(),
                events: parsedEvents,
                endpoint: form.elements.endpoint.value.trim(),
                secret: form.elements.secret.value.trim(),
            }),
        })
        submitBtn.classList.remove('disabled')

        // Display a success/error message
        if (!response.ok) {
            showMessage('error', `Error saving the webhook: ${response.status} ${response.statusText}: ${await response.text()}.`)
        } else {
            showMessage('success', `New webhook successfully created. Reload this page`)
            form.reset()
        }
    })

    const deleteBtns = document.getElementsByClassName('webhook-delete-btn')
    for (const deleteBtn of deleteBtns) {
        deleteBtn.onclick = async () => {
            const id = deleteBtn.getAttribute('data-id')
            const body = new URLSearchParams()
            body.set('id', id)
            const response = await fetch(restURL, {
                method: 'DELETE',
                body,
            })
            if (response.ok) {
                deleteBtn.parentElement.parentElement.remove()
            } else {
                alert('Deleting webhook failed')
            }
        }
    }
})
