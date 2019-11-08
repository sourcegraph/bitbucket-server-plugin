package com.sourcegraph.webhook;

import com.google.gson.Gson;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookException;
import com.sourcegraph.webhook.registry.WebhookRegistry;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Path("/webhook")
public class WebhookRouter {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        List<Webhook> hooks = WebhookRegistry.getWebhooks();
        String resp = new Gson().toJson(hooks);
        return Response.ok(resp).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response put(
            @FormParam("scope") String scope,
            @FormParam("identifier") String identifier,
            @FormParam("events") String events,
            @FormParam("external") String external) {
        // TODO - Form Data Validation
        Webhook hook = new Webhook(0, scope, identifier, new HashSet<>(), external);
        Collections.addAll(hook.events, events.split(","));
        System.out.println(hook.events.size());
        System.out.println("REGISTERING: " + new Gson().toJson(hook));

        try {
            WebhookRegistry.register(hook);
        } catch (WebhookException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }

        return Response.ok().build();
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response delete(@FormParam("id") int id) {
        WebhookRegistry.deregister(id);
        return Response.noContent().build();
    }
}
