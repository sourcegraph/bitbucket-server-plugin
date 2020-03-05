package com.sourcegraph.webhook.registry;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import java.util.Date;

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

    String getLastError();
    void setLastError(String error);

    Date getLastEvent();
    void setLastEvent(Date date);
}
