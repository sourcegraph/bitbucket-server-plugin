package com.sourcegraph.webhook;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sourcegraph.webhook.registry.Webhook;
import com.sourcegraph.webhook.registry.WebhookRegistry;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class Dispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
    private static final int[] RETRY_DELAYS = {10, 20, 60, 120, 300};
    private static final int MAX_ATTEMPTS = 5;
    @ComponentImport
    private static ScheduledExecutorService executor;
    @ComponentImport
    private static RequestFactory requestFactory;

    private Webhook hook;
    private Request request;
    private int attempt = 1;
    private String name;

    @Autowired
    public Dispatcher(ScheduledExecutorService executor, RequestFactory<?> requestFactory) {
        Dispatcher.executor = executor;
        Dispatcher.requestFactory = requestFactory;
    }

    public Dispatcher(Webhook hook, String name, String payload) {
        this.hook = hook;
        this.name = name;
        this.request = createRequest(hook, name, payload);
    }

    public Dispatcher(Webhook hook, EventSerializer serializer) {
        this.hook = hook;
        this.name = serializer.getName();
        JsonObject payload = serializer.serialize();
        String json = new Gson().toJson(payload);
        this.request = createRequest(hook, this.name, json);
    }

    private static String sign(String secret, String data) {
        HmacUtils hmac = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret);
        return "sha256=" + hmac.hmacHex(data);
    }

    private static Request createRequest(Webhook hook, String name, String json) {
        Request request = requestFactory.createRequest(Request.MethodType.POST, hook.endpoint);
        request.setHeader("X-Event-Key", name);
        request.setHeader("X-Hook-ID", String.valueOf(hook.id));
        request.setHeader("X-Hook-Name", hook.name);

        request.setRequestBody(json);
        request.setHeader("X-Hub-Signature", sign(hook.secret, json));
        return request;
    }

    @Override
    public void run() {
        request.setHeader("X-Attempt-Number", String.valueOf(attempt));
        try {
            Response response = (Response) request.executeAndReturn((resp) -> resp);
            if (response.isSuccessful()) {
                log.debug("Successfully dispatched webhook (" + this.name + ") data to URL: [" + hook.endpoint + "].");
                WebhookRegistry.storeError(this.hook, null);
                return;
            }
            String error = String.format("Failed to dispatch webhook: %d %s", response.getStatusCode(), response.getStatusText());
            WebhookRegistry.storeError(this.hook, error);
            log.warn(this.hookLogMessage(this.hook, error));
        } catch (ResponseException e) {
            WebhookRegistry.storeError(this.hook, "Failed: " + e.getMessage());
            log.warn(this.hookLogMessage(this.hook, e.getMessage()));
        }

        if (attempt == MAX_ATTEMPTS) {
            log.warn("Exceeded maximum (" + MAX_ATTEMPTS + ") attempts to dispatch webhook data (" + this.name + ") to URL: [" + hook.endpoint + "]");
            return;
        }

        attempt++;

        Dispatcher.executor.schedule(this, RETRY_DELAYS[attempt - 1], TimeUnit.SECONDS);
    }

    private String hookLogMessage(Webhook hook, String error) {
        return "Failed to dispatch webhook data (" + this.name + ") to URL: [" + hook.endpoint + "]:\n" + error;
    }

    public static void dispatch(Webhook hook, EventSerializer serializer) {
        executor.submit(new Dispatcher(hook, serializer));
    }

    public static void dispatch(Webhook hook, String name, String json) {
        executor.submit(new Dispatcher(hook, name, json));
    }
}
