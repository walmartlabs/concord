package com.walmartlabs.concord.examples.customtask;

import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A custom task example. All tasks must implement {@link Task} interface and
 * be marked with {@link Named} annotation.
 *
 * The task fetches a specified URL and parses the response as JSON, converting
 * the data into regular Java objects.
 */
@Named("customTask")
public class CustomTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(CustomTaskV2.class);

    @Override
    public TaskResult execute(Variables input) throws Exception {
        String url = input.assertString("url");

        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url(url)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                log.warn("Error while fetching {}: {}", url, resp.code());
                return TaskResult.error("Error while fetching")
                        .value("errorCode", resp.code());
            }

            ObjectMapper om = new ObjectMapper();
            Object data = om.readValue(resp.body().byteStream(), Object.class);

            return TaskResult.success()
                    .value("data", data);
        }
    }
}
