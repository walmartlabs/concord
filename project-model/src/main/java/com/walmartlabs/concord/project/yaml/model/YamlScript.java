package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;
import io.takari.bpm.model.ScriptTask;

public class YamlScript extends YamlStep {

    private final ScriptTask.Type type;
    private final String language;
    private final String body;

    public YamlScript(JsonLocation location, ScriptTask.Type type, String language, String body) {
        super(location);
        this.type = type;
        this.language = language;
        this.body = body;
    }

    public ScriptTask.Type getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "YamlScript{" +
                "type=" + type +
                ", language='" + language + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}
