package com.sourcegraph.webhook;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookException;
import com.sourcegraph.webhook.registry.WebhookRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/webhook")
@Component
public class WebhookRouter {
    private static final Logger log = LoggerFactory.getLogger(WebhookRouter.class);
    private static UserManager userManager;

    @Autowired
    public WebhookRouter(@ComponentImport UserManager userManager) {
        WebhookRouter.userManager = userManager;
    }

    private void authorize(HttpServletRequest request) throws WebhookException {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null || !userManager.isSystemAdmin(user.getUserKey())) {
            throw new WebhookException(Response.Status.UNAUTHORIZED);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) throws WebhookException {
        authorize(request);
        List<Webhook> hooks = WebhookRegistry.getWebhooks();
        String resp = new Gson().toJson(hooks);
        return Response.ok(resp).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@Context HttpServletRequest request, String raw) throws WebhookException {
        authorize(request);
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

        return Response.noContent().build();
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response delete(@Context HttpServletRequest request, @FormParam("id") int id, @FormParam("name") String name) throws WebhookException {
        authorize(request);
        if (name != null) {
            WebhookRegistry.deregister(name);
        } else {
            WebhookRegistry.deregister(id);
        }

        return Response.noContent().build();
    }
}
