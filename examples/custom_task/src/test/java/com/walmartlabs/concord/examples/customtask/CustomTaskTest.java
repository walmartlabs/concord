package com.walmartlabs.concord.examples.customtask;

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CustomTaskTest {

    @Test
    public void test() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("url", "https://jsonplaceholder.typicode.com/todos/1");

        CustomTaskV2 task = new CustomTaskV2();
        TaskResult.SimpleResult taskResult = (TaskResult.SimpleResult) task.execute(new MapBackedVariables(args));
        Map<String, Object> result = taskResult.toMap();
        assertEquals(true, result.get("ok"));

        System.out.println(result.get("data"));
    }
}
