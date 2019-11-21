package com.sourcegraph.webhook.registry;

import com.google.gson.annotations.Expose;

import java.util.Set;

public class Webhook {
    @Expose
    public int id;
    @Expose
    public String name;
    @Expose
    public String scope;
    @Expose
    public Set<String> events;
    @Expose
    public String endpoint;
    @Expose(serialize = false)
    public String secret;

    public Webhook(int id, String name, String scope, Set<String> events, String endpoint, String secret) {
        this.id = id;
        this.name = name;
        this.scope = scope;
        this.events = events;
        this.endpoint = endpoint;
        this.secret = secret;
    }
}
