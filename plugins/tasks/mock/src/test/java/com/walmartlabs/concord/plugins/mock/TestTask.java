package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Named;

@Named("testTask")
public class TestTask implements Task {

    @Override
    public TaskResult execute(Variables input) {
        return TaskResult.success();
    }

    public String doAction(String input) {
        return input;
    }
}
