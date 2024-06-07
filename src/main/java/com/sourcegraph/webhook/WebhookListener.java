package com.sourcegraph.webhook;

import com.atlassian.bitbucket.build.status.RepositoryBuildStatusSetEvent;
import com.atlassian.bitbucket.event.ApplicationEvent;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.event.repository.RepositoryPushEvent;
import com.atlassian.bitbucket.pull.PullRequestAction;
import com.atlassian.bitbucket.pull.PullRequestParticipantStatus;
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
    public void onPullRequestEvent(PullRequestEvent event) {
        String key = "pr:event";

        // When review status changes, two events are fired in in all cases apart from
        // when we move from Needs Work to Unapproved.
        // 1. A PR Activity Event
        // 2. A PR Participant Event
        // We only want to forward the Activity events.
        // However, when moving from Needs Works to Unapproved, no activity event is fired
        // and so in this one case we want to forward the Participant Event
        if (event instanceof PullRequestParticipantStatusUpdatedEvent) {
            PullRequestAction action = event.getAction();
            PullRequestParticipantStatus previousStatus = ((PullRequestParticipantStatusUpdatedEvent) event).getPreviousStatus();
            if (action != PullRequestAction.UNAPPROVED) {
                return;
            }
            if (previousStatus != PullRequestParticipantStatus.NEEDS_WORK) {
                return;
            }
            key =  "pr:participant:status";
        }

        if (event instanceof PullRequestActivityEvent) {
            key = "pr:activity:event";
        }
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
    public void onRepositoryBuildStatusSetEvent(RepositoryBuildStatusSetEvent event) {
        handle(event, "repo:build_status");
    }

    @EventListener
    public void onRepositoryPushEvent(RepositoryPushEvent event) {
        handle(event, "repo:refs_changed");
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
