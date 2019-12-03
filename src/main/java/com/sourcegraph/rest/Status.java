package com.sourcegraph.rest;

import javax.ws.rs.core.Response;

public enum Status implements Response.StatusType {
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity");

    private final int code;
    private final String reason;

    Status(int status, String reason) {
        this.code = status;
        this.reason = reason;
    }

    public int getStatusCode() {
        return this.code;
    }

    public Response.Status.Family getFamily() {
        return null;
    }

    public String getReasonPhrase() {
        return this.reason;
    }
}