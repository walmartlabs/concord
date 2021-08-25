package com.walmartlabs.concord.examples.customtask;

import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.MockContext;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CustomTaskTest {

    @Test
    public void test() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("url", "https://jsonplaceholder.typicode.com/todos/1");

        MockContext ctx = new MockContext(args);

        CustomTask task = new CustomTask();
        task.execute(ctx);

        Map<String, Object> result = ContextUtils.getMap(ctx, "result", Collections.emptyMap());
        assertEquals(true, result.get("ok"));

        System.out.println(result.get("data"));
    }
}
