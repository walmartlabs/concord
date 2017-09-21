package com.walmartlabs.concord.plugins.example;

import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.JavaDelegate;

import javax.inject.Named;

@Named("exampleDelegate")
@Deprecated
public class ExampleDelegate implements Task, JavaDelegate {

    @Override
    public void execute(ExecutionContext ctx) throws Exception {
        ctx.setVariable("exampleOutput", "Hello!");
    }
}
