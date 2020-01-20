package com.sourcegraph.webhook.registry;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.sourcegraph.rest.Status;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
                repository.getProject().getId(),
                repository.getId()
        ));

        String where = "event.EVENT in (" + params + ") "
                + "AND (webhook.SCOPE = \'global\' "
                + "OR (webhook.SCOPE = CONCAT(\'project\', ':',  ?)) "
                + "OR (webhook.SCOPE = CONCAT(\'repository\', ':', ?)))";

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

            String selector = resolveScopeName(ent.getScope());
            hooks.add(new Webhook(ent.getID(), ent.getName(), selector, events, ent.getEndpoint(), ent.getSecret()));
        }
        return hooks;
    }

    public static void register(Webhook hook) throws WebhookException {
        String scope = resolveScopeID(hook.scope);

        activeObjects.executeInTransaction(() -> {
            WebhookEntity[] ents = activeObjects.find(WebhookEntity.class, Query.select().where("ID = ?", hook.id));
            WebhookEntity ent = ents.length == 0 ? activeObjects.create(WebhookEntity.class) : ents[0];
            ent.setName(hook.name);
            ent.setScope(scope);
            ent.setScope(hook.endpoint);
            ent.setScope(hook.secret);
            ent.save();

            // This logic applies the diff between registered and wanted events.
            HashSet<String> add = new HashSet<>(hook.events);

            // Remove older events
            for (EventEntity event : ent.getEvents()) {
                if (!add.contains(event.getEvent())) {
                    activeObjects.delete(event);
                }
                add.remove(event);
            }

            // Add new ones
            for (String event : add) {
                EventEntity eventEntity = activeObjects.create(EventEntity.class);
                eventEntity.setWebhook(ent);
                eventEntity.setEvent(event);
                eventEntity.save();
            }

            return null;
        });
    }

    public static void deregister(int id) {
        activeObjects.executeInTransaction(() -> {
            activeObjects.deleteWithSQL(EventEntity.class, "WEBHOOK_ID = ?", id);
            activeObjects.deleteWithSQL(WebhookEntity.class, "ID = ?", id);
            return null;
        });
    }

    public static void deregister(String name) {
        activeObjects.executeInTransaction(() -> {
            WebhookEntity[] hooks = activeObjects.find(WebhookEntity.class, "NAME = ?", name);
            for (WebhookEntity hook : hooks) {
                for (EventEntity event : hook.getEvents()) {
                    activeObjects.delete(event);
                }
                activeObjects.delete(hook);
            }
            return null;
        });
    }

    private static String resolveScopeID(String scope) throws WebhookException {
        if (scope == null) {
            throw new WebhookException(Status.UNPROCESSABLE_ENTITY, "Missing scope");
        }

        if (scope.equals("global")) {
            return scope;
        }

        String[] split = scope.split(":");
        if (split.length < 2) {
            throw new WebhookException(Status.UNPROCESSABLE_ENTITY, "Invalid scope: " + scope);
        }

        String selector = split[0];
        String name = split[1];
        switch (selector) {
            case "repository":
                String[] slug = name.split("/");
                Repository repository = repositories.getBySlug(slug[0], slug[1]);
                if (repository == null) {
                    throw new WebhookException(Response.Status.NOT_FOUND, "No such repository: " + name);
                }
                return selector + ":" + repository.getId();
            case "project":
                Project project = projects.getByName(name);
                if (project == null) {
                    throw new WebhookException(Response.Status.NOT_FOUND, "No such project: " + name);
                }
                return selector + ":" + project.getId();
            default:
                throw new WebhookException(Status.UNPROCESSABLE_ENTITY, "Invalid scope: " + scope);
        }
    }

    private static String resolveScopeName(String scope) {
        if (scope.equals("global")) {
            return scope;
        }

        String[] split = scope.split(":");
        String selector = split[0];
        int id = Integer.parseInt(split[1]);
        switch (selector) {
            case "repository":
                Repository repository = repositories.getById(id);
                return repository == null ? "" : selector + ":" + repository.getProject().getName() + "/" + repository.getName();
            case "project":
                Project project = projects.getById(id);
                return project == null ? "" : selector + ":" + project.getName();
            default:
                return "";
        }
    }
}

