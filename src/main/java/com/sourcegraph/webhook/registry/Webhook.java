package com.sourcegraph.webhook.registry;

import java.util.Set;

public class Webhook {
    public int id;
    public String scope;
    public String identifier;
    public Set<String> events;
    public String external;

    public Webhook(int id, String scope, String identifier, Set<String> events, String external) {
        this.id = id;
        this.scope = scope;
        this.identifier = identifier;
        this.events = events;
        this.external = external;
    }
}