package com.walmartlabs.concord.runtime.v2.runner.functions;

import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;

import javax.inject.Named;

@Named
public class TestFunction {

    @ELFunction("testGreet")
    public static String regularGreet(String name) {
        return "Hi, " + name + "!";
    }

    @ELFunction("testFunction:greet")
    public static String prefixedGreet(String name) {
        return "Hello, " + name + "!";
    }
}
