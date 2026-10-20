package com.sourcegraph.webhook.registry;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebhookException extends Exception implements ExceptionMapper<WebhookException> {
    private Response.StatusType status;

    public WebhookException() {
    }

    public WebhookException(Response.StatusType status) {
        this.status = status;
    }

    public WebhookException(Response.StatusType status, String message) {
        super(message);
        this.status = status;
    }

    public Response.StatusType getStatus() {
        return status;
    }

    @Override
    public Response toResponse(WebhookException e) {
        return Response.status(e.getStatus()).entity(e.getMessage()).build();
    }
}
