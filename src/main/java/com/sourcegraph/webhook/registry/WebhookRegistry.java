package com.sourcegraph.webhook.registry;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WebhookRegistry {
    @ComponentImport
    private static ActiveObjects activeObjects;

    @Autowired
    public WebhookRegistry(ActiveObjects ao) {
        WebhookRegistry.activeObjects = ao;
    }

    public static List<Webhook> getWebhooks() {
        WebhookEntity[] entities = activeObjects.find(WebhookEntity.class);
        return getWebhooksFromEntities(entities);
    }

    public static List<Webhook> getWebhooks(List<String> keys, Repository repository) {
        String params = Joiner.on(", ").join(Collections.nCopies(keys.size(), "?"));
        Iterable<String> args = Iterables.concat(keys, Arrays.asList(
                repository.getName(),
                repository.getProject().getName()
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

            hooks.add(new Webhook(ent.getID(), ent.getScope(), ent.getIdentifier(), events, ent.getExternal()));
        }
        return hooks;
    }

    public static void register(Webhook hook) {
        activeObjects.executeInTransaction(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("SCOPE", hook.scope);
            params.put("IDENTIFIER", hook.identifier);
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
}
