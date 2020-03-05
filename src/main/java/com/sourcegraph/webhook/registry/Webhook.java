package com.sourcegraph.webhook.registry;

import com.google.gson.annotations.Expose;

import java.util.Set;
import java.util.Date;

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
    @Expose
    public String lastError;
    @Expose
    public Date lastEvent;

    public Webhook(int id, String name, String scope, Set<String> events, String endpoint, String secret, String lastError, Date lastEvent) {
        this.id = id;
        this.name = name;
        this.scope = scope;
        this.events = events;
        this.endpoint = endpoint;
        this.secret = secret;
        this.lastError = lastError;
        this.lastEvent = lastEvent;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getScope() {
        return this.scope;
    }

    public Set<String> getEvents() {
        return this.events;
    }

    public String getLastError() {
        return this.lastError;
    }

    public Date getLastEvent() {
        return this.lastEvent;
    }
}
