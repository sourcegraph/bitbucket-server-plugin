package com.sourcegraph.webhook.registry;

import net.java.ao.Entity;
import net.java.ao.OneToMany;

public interface WebhookEntity extends Entity {
    String getName();
    void setName(String name);

    String getScope();
    void setScope(String scope);

    @OneToMany
    EventEntity[] getEvents();

    String getEndpoint();
    void setEndpoint(String endpoint);

    String getSecret();
    void setSecret(String secret);
}
