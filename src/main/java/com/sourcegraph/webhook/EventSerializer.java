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
    private static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd'T'h:m:ssZZZZZ");
    private static Map<Class<?>, Adapter> adapters = new HashMap<>();
    private static JsonRenderer renderer;

    private ApplicationEvent event;
    private String name;
    private JsonObject payload;

    private static JsonElement render(Object o) {
        HashMap<String, Object> options = new HashMap<>();
        String raw = renderer.render(o, options);
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

    public EventSerializer(String name, ApplicationEvent event) {
        payload = new JsonObject();
        this.name = name;
        this.event = event;
    }

    public JsonObject serialize() {
        buildApplicationEvent(this.event);
        if (event instanceof PullRequestEvent) {
            buildPullRequestEvent((PullRequestEvent) event);
        }

        Adapter adapter = adapters.get(event.getClass());
        if (adapter != null) {
            adapter.apply(payload, event);
        }
        return payload;
    }

    public String getName() {
        return this.name;
    }

    public ApplicationEvent getEvent() {
        return this.event;
    }

    private void buildApplicationEvent(ApplicationEvent event) {
        payload.addProperty("date", RFC3339.format(event.getDate()));
        payload.add("actor", render(event.getUser()));
    }

    private void buildPullRequestEvent(PullRequestEvent event) {
        payload.add("pullRequest", render(event.getPullRequest()));
    }

    private interface Adapter<T> {
        void apply(JsonObject element, T event);
    }
}
