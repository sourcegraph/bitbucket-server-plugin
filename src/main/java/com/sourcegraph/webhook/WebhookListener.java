package com.sourcegraph.webhook;

import com.atlassian.bitbucket.build.BuildStatusSetEvent;
import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.event.api.AsynchronousPreferred;
import com.atlassian.event.api.EventListener;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookRegistry;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

@AsynchronousPreferred
@Named("WebhookListener")
public class WebhookListener {

    // getTriggers enumerate all prefixes (or super/parent events) of event key.
    // "pr:comment:added" -> ["pr", "pr:comment", "pr:comment:added"]
    public List<String> getTriggers(String key) {
        String[] split = key.split(":");
        if (split.length == 0) {
            return null;
        }

        List<String> prefixes = new ArrayList<>();
        for (int i = 0, index = 0; i < split.length; i++) {
            index += split[i].length();
            prefixes.add(key.substring(0, index));
            index++;
        }
        return prefixes;
    }

    @EventListener
    public void onPullRequestEvent(PullRequestActivityEvent event) {
        String key = "pr:activity:status";
        if (event instanceof PullRequestRescopeActivityEvent) {
            key = "pr:activity:rescope";
        } else if (event instanceof PullRequestMergeActivityEvent) {
            key = "pr:activity:merge";
        } else if (event instanceof PullRequestCommentActivityEvent) {
            key = "pr:activity:comment";
        } else if (event instanceof PullRequestReviewersUpdatedActivityEvent) {
            key = "pr:activity:reviewers";
        }

        handle(event, key, event.getPullRequest().getToRef().getRepository());
    }

    @EventListener
    public void onBuildStatusEvent(BuildStatusSetEvent event) {
        handle(event, "repo:build_status");
    }

    public void handle(ApplicationEvent event, String key) {
        handle(event, key, null);
    }

    private void handle(ApplicationEvent event, String key, Repository repository) {
        List<String> triggers = getTriggers(key);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }

        List<Webhook> hooks = WebhookRegistry.getWebhooks(triggers, repository);
        if (hooks.isEmpty()) {
            return;
        }
        hooks.forEach(hook -> {
            EventSerializer serializer = new EventSerializer(key, event);
            Dispatcher.dispatch(hook, serializer);
        });
    }
}
