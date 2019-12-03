package com.sourcegraph.webhook.registry;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

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
