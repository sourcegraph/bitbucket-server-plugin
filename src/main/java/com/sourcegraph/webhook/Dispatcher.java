package com.sourcegraph.webhook;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.RequestFactory;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
    private static final int RETRY_DELAY = 10 * 1000;
    @ComponentImport
    private static ExecutorService executor;
    @ComponentImport
    private static RequestFactory requestFactory;

    @Autowired
    public Dispatcher(ExecutorService executor, RequestFactory<?> requestFactory) {
        Dispatcher.executor = executor;
        Dispatcher.requestFactory = requestFactory;
    }

    public static void dispatch(String external, JsonElement payload) {
        // System.out.println(external + " " + new Gson().toJson(payload));

        executor.submit(() -> {
            Request request = requestFactory.createRequest(Request.MethodType.POST, external);
            request.setRequestBody(new Gson().toJson(payload));

            int retries = 5;
            while (true) {
                Response response = null;
                try {
                    response = (Response) request.executeAndReturn((resp) -> resp);
                } catch (ResponseException e) {
                    log.debug("Dispatching webhook data to URL: [" + external + "] failed with error:\n" + e);
                }
                retries--;

                if (response != null && response.isSuccessful()) {
                    break;
                }

                if (retries == 0) {
                    log.warn("Dispatching webhook data to URL: [" + external + "] failed after 5 attempts..");
                    break;
                }

                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    log.debug("Dispatching webhook data to URL: [" + external + "] was interrupted.");
                    break;
                }
            }
        });
    }
}
