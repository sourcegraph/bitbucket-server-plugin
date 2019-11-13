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
import org.omg.CORBA.DynAnyPackage.Invalid;
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
    private static final int MAX_ATTEMPTS = 5;
    @ComponentImport
    private static ExecutorService executor;
    @ComponentImport
    private static RequestFactory requestFactory;

    @Autowired
    public Dispatcher(ExecutorService executor, RequestFactory<?> requestFactory) {
        Dispatcher.executor = executor;
        Dispatcher.requestFactory = requestFactory;
    }

    private static String sign(String secret, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKeySpec);
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static Request createRequest(Webhook hook, EventSerializer serializer) {
        Request request = requestFactory.createRequest(Request.MethodType.POST, hook.endpoint);
        request.setHeader("X-Event-Key", serializer.getName());
        JsonObject payload = serializer.serialize();
        String json = new Gson().toJson(payload);
        request.setRequestBody(json);

        try {
            request.setHeader("X-Signature", sign(hook.secret, json));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            log.error(e.toString());
            return null;
        }

        return request;
    }

    public static void dispatch(Webhook hook, EventSerializer serializer) {
        executor.submit(() -> {
            Request request = createRequest(hook, serializer);
            if (request == null) {
                return;
            }

            int attempt = 0;
            while (true) {
                request.setHeader("X-Attempt-Number", String.valueOf(attempt));
                try {
                    Response response = (Response) request.executeAndReturn((resp) -> resp);
                    if (response.isSuccessful()) {
                        log.debug("Dispatching webhook (" + serializer.getName() + ") data to URL: [" + hook.endpoint + "] succeeded.");
                        break;
                    }
                } catch (ResponseException e) {
                    log.debug("Dispatching webhook data (" + serializer.getName() + ") to URL: [" + hook.endpoint + "] failed with error:\n" + e);
                }
                attempt++;

                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Dispatching webhook data (" + serializer.getName() + ") to URL: [" + hook.endpoint + "] failed after " + attempt + " attempts..");
                    break;
                }

                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    log.debug("Dispatching webhook data (" + serializer.getName() + ") to URL: [" + hook.endpoint + "] was interrupted.");
                    break;
                }
            }
        });
    }

}
