package com.sourcegraph.webhook.registry;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class WebhookException extends Exception implements ExceptionMapper<WebhookException> {
    private Response.StatusType status;

    public WebhookException() {
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

}
