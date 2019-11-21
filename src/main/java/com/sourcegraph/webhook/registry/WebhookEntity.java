package com.sourcegraph.webhook.registry;

import net.java.ao.Entity;
import net.java.ao.OneToMany;

public interface WebhookEntity extends Entity {
    String getName();

    String getScope();

    @OneToMany
    EventEntity[] getEvents();

    String getEndpoint();

    String getSecret();
}
