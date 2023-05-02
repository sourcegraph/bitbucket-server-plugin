package com.sourcegraph.webhook;

import com.atlassian.bitbucket.build.status.RepositoryBuildStatusSetEvent;
import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.PullRequestActivityEvent;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.event.pull.PullRequestMergeActivityEvent;
import com.atlassian.bitbucket.event.pull.PullRequestReviewersUpdatedActivityEvent;
import com.atlassian.bitbucket.json.JsonRenderer;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestCommitSearchRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.util.*;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventSerializer {
    @ComponentImport
    private static PullRequestService pullRequestService;

    private static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd'T'h:m:ssZZZZZ");
    private static Map<String, Adapter> adapters = new HashMap<>();
    private static JsonRenderer renderer;

    private ApplicationEvent event;
    private String name;
    private JsonObject payload;

    private static JsonElement render(Object o) {
        if (o == null) {
            return null;
        }

        HashMap<String, Object> options = new HashMap<>();
        String raw = renderer.render(o, options);
        return raw == null ? null : new JsonParser().parse(raw);
    }

    private static Adapter<RepositoryBuildStatusSetEvent> RepositoryBuildStatusSetEventAdapter = ((element, event) -> {
        element.addProperty("commit", event.getBuildStatus().getCommitId());
        element.add("status", render(event.getBuildStatus()));

        // Find Pull Requests
        PullRequestCommitSearchRequest searchRequest = new PullRequestCommitSearchRequest.Builder(event.getBuildStatus().getCommitId()).build();
        PageProvider<PullRequest> pager = (pageRequest) -> pullRequestService.searchByCommit(searchRequest, pageRequest);

        JsonArray ja = new JsonArray();
        for (PullRequest pr : PageUtils.toIterable(pager,1000)) {
            ja.add(render(pr));
        }

        element.add("pullRequests", ja);
    });

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
        adapters.put("pr:activity:merge", PullRequestMergeActivityEventAdapter);
        adapters.put("pr:activity:reviewers", PullRequestReviewersUpdatedActivityEventAdapter);
        adapters.put("repo:build_status", RepositoryBuildStatusSetEventAdapter);
    }

    @Autowired
    public EventSerializer(@ComponentImport JsonRenderer renderer, PullRequestService pullRequestService) {
        EventSerializer.renderer = renderer;
        EventSerializer.pullRequestService = pullRequestService;
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

        if (event instanceof PullRequestActivityEvent) {
            buildPullRequestActivityEvent((PullRequestActivityEvent) event);
        }

        Adapter adapter = adapters.get(this.name);
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
        payload.addProperty("createdDate", event.getDate().toInstant().toEpochMilli());
        payload.add("user", render(event.getUser()));
    }

    private void buildPullRequestEvent(PullRequestEvent event) {
        payload.add("pullRequest", render(event.getPullRequest()));
        payload.addProperty("action", event.getAction().toString());
    }

    private void buildPullRequestActivityEvent(PullRequestActivityEvent event) {
        payload.add("activity", render(event.getActivity()));
    }

    private interface Adapter<T> {
        void apply(JsonObject element, T event);
    }
}
