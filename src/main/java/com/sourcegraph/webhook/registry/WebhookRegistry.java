package com.sourcegraph.webhook.registry;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.*;

@Component
public class WebhookRegistry {
    @ComponentImport
    private static ActiveObjects activeObjects;
    @ComponentImport
    private static ProjectService projects;
    @ComponentImport
    private static RepositoryService repositories;

    @Autowired
    public WebhookRegistry(ActiveObjects ao, ProjectService projects, RepositoryService repositories) {
        WebhookRegistry.activeObjects = ao;
        WebhookRegistry.projects = projects;
        WebhookRegistry.repositories = repositories;
    }

    public static List<Webhook> getWebhooks() {
        WebhookEntity[] entities = activeObjects.find(WebhookEntity.class);
        return getWebhooksFromEntities(entities);
    }

    public static List<Webhook> getWebhooks(List<String> keys, Repository repository) {
        String params = Joiner.on(", ").join(Collections.nCopies(keys.size(), "?"));
        Iterable<Object> args = Iterables.concat(keys, Arrays.asList(
                repository.getId(),
                repository.getProject().getId()
        ));

        String where = "event.EVENT in (" + params + ") "
                + "AND (webhook.SCOPE = \'global\' "
                + "OR (webhook.SCOPE = \'project\' AND webhook.IDENTIFIER = ?) "
                + "OR (webhook.SCOPE = \'repository\' AND webhook.IDENTIFIER = ?))";

        Query query = Query.select()
                .alias(WebhookEntity.class, "webhook")
                .alias(EventEntity.class, "event")
                .join(EventEntity.class, "event.WEBHOOK_ID = webhook.ID")
                .where(where, Iterables.toArray(args, Object.class));

        WebhookEntity[] hooks = activeObjects.find(WebhookEntity.class, query);
        return getWebhooksFromEntities(hooks);
    }

    private static List<Webhook> getWebhooksFromEntities(WebhookEntity[] entities) {
        List<Webhook> hooks = new ArrayList<>(entities.length);

        for (WebhookEntity ent : entities) {
            Set<String> events = new HashSet<>();
            for (EventEntity ev : ent.getEvents()) {
                events.add(ev.getEvent());
            }

            String name = resolveName(ent.getScope(), ent.getIdentifier());
            hooks.add(new Webhook(ent.getID(), ent.getScope(), name, events, ent.getExternal()));
        }
        return hooks;
    }

    public static void register(Webhook hook) throws WebhookException {
        int identifier = resolveID(hook.scope, hook.identifier);

        activeObjects.executeInTransaction(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("SCOPE", hook.scope);
            params.put("IDENTIFIER", identifier);
            params.put("EXTERNAL", hook.external);

            WebhookEntity hookEntity = activeObjects.create(WebhookEntity.class, params);
            hookEntity.save();

            for (String event : hook.events) {
                EventEntity eventEntity = activeObjects.create(EventEntity.class);
                eventEntity.setEvent(event);
                eventEntity.setWebhook(hookEntity);
                eventEntity.save();
            }

            return hookEntity;
        });
    }

    public static void deregister(int id) {
        activeObjects.executeInTransaction(() -> {
            activeObjects.deleteWithSQL(EventEntity.class, "WEBHOOK_ID = ?", id);
            activeObjects.deleteWithSQL(WebhookEntity.class, "ID = ?", id);
            return null;
        });
    }

    private static int resolveID(String scope, String name) throws WebhookException {
        switch (scope) {
            case "repository":
                String[] split = name.split("/");
                Repository repository = repositories.getBySlug(split[0], split[1]);
                if (repository == null) {
                    throw new WebhookException(Response.Status.NOT_FOUND, "No such repository: " + name);
                }
                return repository.getId();
            case "project":
                Project project = projects.getByName(name);
                if (project == null) {
                    throw new WebhookException(Response.Status.NOT_FOUND, "No such project: " + name);
                }
                return project.getId();
            case "global":
                return 0;
            default:
                throw new WebhookException(WebhookException.Status.UNPROCESSABLE_ENTITY, "Invalid scope: " + scope);
        }
    }

    private static String resolveName(String scope, int id) {
        switch (scope) {
            case "repository":
                Repository repository = repositories.getById(id);
                return repository == null ? "" : repository.getName();
            case "project":
                Project project = projects.getById(id);
                return project == null ? "" : project.getName();
            default:
                return "";
        }
    }
}
