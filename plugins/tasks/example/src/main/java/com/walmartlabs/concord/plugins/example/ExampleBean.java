package com.walmartlabs.concord.plugins.example;

import java.io.Serializable;

public class ExampleBean implements Serializable {

    private final String value;

    public ExampleBean(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ExampleBean{" +
                "value='" + value + '\'' +
                '}';
    }
}
