package com.walmartlabs.concord.examples.customtask;

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;

@Named("reposolns")
public class CustomTask implements Task {

    @Inject
    public CustomTask(Context context) {
        System.out.println("ctor");
    }

    @Override
    public TaskResult execute(Variables input) {
        System.out.println("execute: " + input.toMap());
        return TaskResult.success();
    }
}