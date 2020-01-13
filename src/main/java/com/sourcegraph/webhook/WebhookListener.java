package com.sourcegraph.webhook;

import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.AsynchronousPreferred;
import com.atlassian.event.api.EventListener;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookRegistry;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AsynchronousPreferred
@Named("WebhookListener")
public class WebhookListener {
    private static Map<Class<?>, List<String>> triggers = new HashMap<>();

    private static void register(Class<?> type, String key) {
        // Enumerate all prefixes (or super/parent events) of event key.
        // "pr:comment:added" -> ["pr", "pr:comment", "pr:comment:added"]
        String[] split = key.split(":");
        List<String> prefixes = new ArrayList<>();
        for (int i = 0, index = 0; i < split.length; i++) {
            index += split[i].length();
            prefixes.add(key.substring(0, index));
            index++;
        }
        triggers.put(type, prefixes);
    }

    static {
        register(PullRequestActivityEvent.class, "pr:activity:status");
        register(PullRequestRescopeActivityEvent.class, "pr:activity:rescope");
        register(PullRequestMergeActivityEvent.class, "pr:activity:merge");
        register(PullRequestCommentActivityEvent.class, "pr:activity:comment");
        register(PullRequestReviewersUpdatedActivityEvent.class, "pr:activity:reviewers");
    }

    @EventListener
    public void onPullRequestEvent(PullRequestActivityEvent event) {
        handle(event, event.getPullRequest().getToRef().getRepository());
    }

    private void handle(ApplicationEvent event, Repository repository) {
        List<String> keys = triggers.get(event.getClass());
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<Webhook> hooks = WebhookRegistry.getWebhooks(keys, repository);
        if (hooks.isEmpty()) {
            return;
        }

        String key = keys.get(keys.size() - 1);
        EventSerializer serializer = new EventSerializer(key, event);
        hooks.forEach(hook -> Dispatcher.dispatch(hook, serializer));
    }
}
