package com.sourcegraph.webhook;

import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.json.JsonRenderer;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventSerializer {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    private static Map<Class<?>, Adapter> adapters = new HashMap<>();
    private static JsonRenderer renderer;

    private JsonObject payload;

    private static JsonElement render(Object o) {
        String raw = renderer.render(o, new HashMap<>());
        return raw == null ? null : new JsonParser().parse(raw);
    }

    private static Adapter<PullRequestUpdatedEvent> PullRequestUpdatedEventAdapter = (element, event) -> {
        element.addProperty("previousTitle", event.getPreviousTitle());
        element.addProperty("previousDescription", event.getPreviousDescription());
        element.add("previousTarget", render(event.getPreviousToBranch()));
    };

    private static Adapter<PullRequestReviewersUpdatedEvent> PullRequestReviewersUpdatedEventAdapter = (element, event) -> {
        element.add("removedReviewers", render(event.getRemovedReviewers()));
        element.add("addedReviewers", render(event.getAddedReviewers()));
    };

    private static Adapter<PullRequestParticipantStatusUpdatedEvent> PullRequestParticipantStatusUpdatedEventAdapter = (element, event) -> {
        element.add("participant", render(event.getParticipant()));
        element.addProperty("previousStatus", event.getPreviousStatus().name());
    };

    private static Adapter<PullRequestCommentEvent> PullRequestCommentEventAdapter = (element, event) -> {
        element.add("comment", render(event.getComment()));
        if (event.getParent() != null) {
            element.addProperty("commentParentId", event.getParent().getId());
        }
    };

    private static Adapter<PullRequestCommentEditedEvent> PullRequestCommentEditedEventAdapter = (element, event) -> {
        PullRequestCommentEventAdapter.apply(element, event);
        element.addProperty("previousComment", event.getPreviousText());
    };

    static {
        adapters.put(PullRequestUpdatedEvent.class, PullRequestUpdatedEventAdapter);
        adapters.put(PullRequestReviewersUpdatedEvent.class, PullRequestReviewersUpdatedEventAdapter);
        adapters.put(PullRequestParticipantApprovedEvent.class, PullRequestParticipantStatusUpdatedEventAdapter);
        adapters.put(PullRequestParticipantUnapprovedEvent.class, PullRequestParticipantStatusUpdatedEventAdapter);
        adapters.put(PullRequestParticipantReviewedEvent.class, PullRequestParticipantStatusUpdatedEventAdapter);
        adapters.put(PullRequestCommentAddedEvent.class, PullRequestCommentEventAdapter);
        adapters.put(PullRequestCommentDeletedEvent.class, PullRequestCommentEventAdapter);
        adapters.put(PullRequestCommentEditedEvent.class, PullRequestCommentEditedEventAdapter);
    }

    @Autowired
    public EventSerializer(@ComponentImport JsonRenderer renderer) {
        EventSerializer.renderer = renderer;
    }

    public EventSerializer(String name) {
        payload = new JsonObject();
        payload.addProperty("eventKey", name);
    }

    public JsonElement serialize(ApplicationEvent event) {
        buildApplicationEvent(event);
        if (event instanceof PullRequestEvent) {
            buildPullRequestEvent((PullRequestEvent) event);
        }

        Adapter adapter = adapters.get(event.getClass());
        if (adapter == null) {
            return payload;
        }
        adapter.apply(payload, event);
        return payload;
    }

    private void buildApplicationEvent(ApplicationEvent event) {
        payload.addProperty("date", DATE_FORMAT.format(event.getDate()));
        payload.add("actor", render(event.getUser()));
    }

    private void buildPullRequestEvent(PullRequestEvent event) {
        payload.add("pullRequest", render(event.getPullRequest()));
    }

    private interface Adapter<T> {
        void apply(JsonObject element, T event);
    }
}
