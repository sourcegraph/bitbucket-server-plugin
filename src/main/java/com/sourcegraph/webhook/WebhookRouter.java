package com.sourcegraph.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookException;
import com.sourcegraph.webhook.registry.WebhookRegistry;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Path("/webhook")
public class WebhookRouter {
    private static final Logger log = LoggerFactory.getLogger(WebhookRouter.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        List<Webhook> hooks = WebhookRegistry.getWebhooks();
        String resp = new Gson().toJson(hooks);
        return Response.ok(resp).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(String raw) throws WebhookException {
        try {
            Gson gson = new Gson();
            Webhook hook = gson.fromJson(raw, Webhook.class);
            if (hook == null) {
                throw new WebhookException(Response.Status.BAD_REQUEST, "Invalid JSON");
            }
            log.info("Registering webhook: " + raw);
            WebhookRegistry.register(hook);
        } catch (JsonIOException e) {
            throw new WebhookException(Response.Status.INTERNAL_SERVER_ERROR, "");
        } catch (JsonSyntaxException e) {
            throw new WebhookException(Response.Status.BAD_REQUEST, "Invalid JSON");
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
