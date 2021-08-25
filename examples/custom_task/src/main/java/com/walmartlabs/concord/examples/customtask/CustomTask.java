package com.walmartlabs.concord.examples.customtask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

/**
 * A custom task example. All tasks must implement {@link Task} interface and
 * be marked with {@link Named} annotation.
 *
 * The task fetches a specified URL and parses the response as JSON, converting
 * the data into regular Java objects.
 */
@Named("customTask")
public class CustomTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(CustomTask.class);

    @Override
    public void execute(Context ctx) throws Exception {
        // retrieve a variable using the helper class
        String url = ContextUtils.assertString(ctx, "url");


        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url(url)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                log.warn("Error while fetching {}: {}", url, resp.code());
                ctx.setVariable("result", error(resp.code()));
                return;
            }

            ObjectMapper om = new ObjectMapper();
            Object data = om.readValue(resp.body().byteStream(), Object.class);

            // do not use custom classes and data structures when setting variables in a task
            // stick to the default JDK classes to avoid serialization issues
            ctx.setVariable("result", ok(data));
        }
    }

    private static Object ok(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("data", data);
        return m;
    }

    private static Object error(int code) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("errorCode", code);
        return m;
    }
}
