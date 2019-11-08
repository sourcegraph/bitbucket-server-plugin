package com.sourcegraph.webhook.registry;

public class WebhookException extends Exception {

    public WebhookException(String message) {
        super(message);
    }
}
