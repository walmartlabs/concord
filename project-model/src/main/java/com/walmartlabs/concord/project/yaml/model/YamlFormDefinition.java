package com.walmartlabs.concord.project.yaml.model;

import io.takari.parc.Seq;

import java.io.Serializable;

public class YamlFormDefinition implements YamlDefinition {

    private final String name;
    private final Seq<YamlFormField> fields;

    public YamlFormDefinition(Seq<YamlFormField> fields) {
        this(null, fields);
    }

    public YamlFormDefinition(String name, Seq<YamlFormField> fields) {
        this.name = name;
        this.fields = fields;
    }

    @Override
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
