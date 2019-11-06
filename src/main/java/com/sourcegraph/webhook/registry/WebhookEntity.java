package com.sourcegraph.webhook.registry;

import net.java.ao.Entity;
import net.java.ao.OneToMany;

public interface WebhookEntity extends Entity {
    String getScope();

    String getIdentifier();

    @OneToMany
    EventEntity[] getEvents();

    String getExternal();
}