package com.sourcegraph.webhook;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sourcegraph.webhook.registry.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;

@Component
public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
    private static final int RETRY_DELAY = 10 * 1000;
    private static final int MAX_RETRIES = 5;
    @ComponentImport
    private static ExecutorService executor;
    @ComponentImport
    private static RequestFactory requestFactory;

    @Autowired
    public Dispatcher(ExecutorService executor, RequestFactory<?> requestFactory) {
        Dispatcher.executor = executor;
        Dispatcher.requestFactory = requestFactory;
    }

    public static String sign(String secret, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKeySpec);
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    public static void dispatch(Webhook hook, JsonObject payload) {

        executor.submit(() -> {
            Request request = requestFactory.createRequest(Request.MethodType.POST, hook.endpoint);

            String json = new Gson().toJson(payload);
            request.setRequestBody(json);

            try {
                request.setHeader("X-Signature", sign(hook.secret, json));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                log.error(e.toString());
            }

            for (int retries = 0; retries < MAX_RETRIES; retries++) {
                Response response = null;
                try {
                    response = (Response) request.executeAndReturn((resp) -> resp);
                } catch (ResponseException e) {
                    log.debug("Dispatching webhook data to URL: [" + hook.endpoint + "] failed with error:\n" + e);
                }

                if (response != null && response.isSuccessful()) {
                    log.debug("Dispatching webhook data to URL: [" + hook.endpoint + "] succeeded.");
                    break;
                }

                if (retries == MAX_RETRIES - 1) {
                    log.warn("Dispatching webhook data to URL: [" + hook.endpoint + "] failed after " + MAX_RETRIES + " attempts..");
                    break;
                }

                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    log.debug("Dispatching webhook data to URL: [" + hook.endpoint + "] was interrupted.");
                    break;
                }
            }
        });

    }
}
