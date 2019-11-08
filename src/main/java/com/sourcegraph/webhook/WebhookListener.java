package com.sourcegraph.webhook;

import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.AsynchronousPreferred;
import com.atlassian.event.api.EventListener;
import com.google.gson.JsonElement;
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
        String[] split = key.split(":");
        List<String> pieces = new ArrayList<>();
        for (int i = 0, index = 0; i < split.length; i++) {
            index += split[i].length();
            pieces.add(key.substring(0, index));
            index++;
        }
        triggers.put(type, pieces);
    }

    static {
        register(PullRequestOpenedEvent.class, "pr:opened");
        register(PullRequestUpdatedEvent.class, "pr:modified");
        register(PullRequestReviewersUpdatedEvent.class, "pr:reviewer:updated");
        register(PullRequestParticipantApprovedEvent.class, "pr:reviewer:approved");
        register(PullRequestParticipantUnapprovedEvent.class, "pr:reviewer:unapproved");
        register(PullRequestParticipantReviewedEvent.class, "pr:reviewer:needs_work");
        register(PullRequestMergedEvent.class, "pr:merged");
        register(PullRequestDeclinedEvent.class, "pr:declined");
        register(PullRequestDeletedEvent.class, "pr:deleted");
        register(PullRequestCommentAddedEvent.class, "pr:comment:added");
        register(PullRequestCommentEditedEvent.class, "pr:comment:edited");
        register(PullRequestCommentDeletedEvent.class, "pr:comment:deleted");
    }

    @EventListener
    public void onPullRequestEvent(PullRequestEvent event) {
        handle(event, event.getPullRequest().getToRef().getRepository());
    }

    private void handle(ApplicationEvent event, Repository repository) {
        System.out.println(event.getClass());
        List<String> keys = triggers.get(event.getClass());
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<Webhook> hooks = WebhookRegistry.getWebhooks(keys, repository);
        if (hooks.isEmpty()) {
            return;
        }

        String key = keys.get(keys.size() - 1);
        EventSerializer serializer = new EventSerializer(key);
        JsonElement payload = serializer.serialize(event);
        hooks.forEach(hook -> Dispatcher.dispatch(hook.external, payload));
    }
}
