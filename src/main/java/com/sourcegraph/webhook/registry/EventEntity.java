package com.sourcegraph.webhook.registry;

import net.java.ao.Entity;

public interface EventEntity extends Entity {
    String getEvent();

    void setEvent(String event);

    WebhookEntity getWebhook();

    void setWebhook(WebhookEntity hook);

}
