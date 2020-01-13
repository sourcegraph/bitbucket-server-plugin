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

    private static Adapter<PullRequestMergeActivityEvent> PullRequestMergeActivityEventAdapter = (element, event) -> {
        JsonObject activity = element.getAsJsonObject("activity");
        activity.add("commit", render(event.getActivity().getCommit()));
    };

    private static Adapter<PullRequestReviewersUpdatedActivityEvent> PullRequestReviewersUpdatedActivityEventAdapter = (element, event) -> {
        JsonObject activity = element.getAsJsonObject("activity");
        activity.add("addedReviewers", render(event.getActivity().getAddedReviewers()));
        activity.add("removedReviewers", render(event.getActivity().getRemovedReviewers()));
    };

    static {
        adapters.put(PullRequestMergeActivityEvent.class, PullRequestMergeActivityEventAdapter);
        adapters.put(PullRequestReviewersUpdatedActivityEvent.class, PullRequestReviewersUpdatedActivityEventAdapter);
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
        if (event instanceof PullRequestActivityEvent) {
            buildPullRequestEvent((PullRequestActivityEvent) event);
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
        payload.addProperty("createdDate", RFC3339.format(event.getDate()));
        payload.add("user", render(event.getUser()));
    }

    private void buildPullRequestEvent(PullRequestActivityEvent event) {
        payload.add("pullRequest", render(event.getPullRequest()));
        payload.add("activity", render(event.getActivity()));
    }

    private interface Adapter<T> {
        void apply(JsonObject element, T event);
    }
}
