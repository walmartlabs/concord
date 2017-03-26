package com.walmartlabs.concord.plugins.yaml2.model;

import io.takari.parc.Seq;

public class YamlFormDefinition implements YamlDefinition {

    private final String name;
    private final Seq<YamlFormField> fields;

    public YamlFormDefinition(String name, Seq<YamlFormField> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public Seq<YamlFormField> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "YamlFormDefinition{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
