package com.sourcegraph.webhook;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sourcegraph.webhook.registry.Webhook;
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
    private static final int RETRY_DELAY = 10;
    private static final int MAX_ATTEMPTS = 5;
    @ComponentImport
    private static ScheduledExecutorService executor;
    @ComponentImport
    private static RequestFactory requestFactory;

    private Webhook hook;
    private EventSerializer serializer;
    private Request request;
    private int attempt;

    @Autowired
    public Dispatcher(ScheduledExecutorService executor, RequestFactory<?> requestFactory) {
        Dispatcher.executor = executor;
        Dispatcher.requestFactory = requestFactory;
    }

    public Dispatcher(Webhook hook, EventSerializer serializer) {
        this.hook = hook;
        this.serializer = serializer;
        this.request = createRequest(hook, serializer);
    }

    private static String sign(String secret, String data) {
        HmacUtils hmac = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret);
        return "sha256=" + hmac.hmacHex(data);
    }

    private static Request createRequest(Webhook hook, EventSerializer serializer) {
        Request request = requestFactory.createRequest(Request.MethodType.POST, hook.endpoint);
        request.setHeader("X-Event-Key", serializer.getName());
        request.setHeader("X-Hook-ID", String.valueOf(hook.id));
        request.setHeader("X-Hook-Name", hook.name);

        JsonObject payload = serializer.serialize();
        String json = new Gson().toJson(payload);
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
                log.debug("Successfully dispatched webhook (" + serializer.getName() + ") data to URL: [" + hook.endpoint + "].");
                return;
            }
        } catch (ResponseException e) {
            log.debug("Failed to dispatch webhook data (" + serializer.getName() + ") to URL: [" + hook.endpoint + "]:\n" + e);
        }
        attempt++;

        if (attempt == MAX_ATTEMPTS) {
            log.warn("Exceeded maximum (" + MAX_ATTEMPTS + ") attempts to dispatch webhook data (" + serializer.getName() + ") to URL: [" + hook.endpoint + "]");
            return;
        }

        Dispatcher.executor.schedule(this, RETRY_DELAY, TimeUnit.SECONDS);
    }

    public static void dispatch(Webhook hook, EventSerializer serializer) {
        executor.submit(new Dispatcher(hook, serializer));
    }
}
