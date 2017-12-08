package com.walmartlabs.concord.plugins.example;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("example")
public class ExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ExampleTask.class);

    @InjectVariable("context")
    private Context context;

    public void hello() {
        log.info("Hello!");
    }

    public void hello(String k) {
        Object o = context.getVariable(k);
        log.info("Hello, {}!", o);
    }

    public void helloButLouder(@InjectVariable("myName") String name) {
        log.info("Hello, {}!!!", name);
    }

    @Override
    public void execute(Context ctx) throws Exception {
        log.info("Hello, {}. (from method param)", ctx.getVariable("myName"));
        log.info("Hello, {}. (from injected var)", context.getVariable("myName"));
        ctx.setVariable("exampleOutput", "Hello!");
    }

    public void call(String a, String b) {
        log.info("We got {} and {}", a, b);
    }
}
