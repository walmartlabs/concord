package com.walmartlabs.concord.plugins.yaml2;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.Serializable;

public class Atom implements Serializable {

    public static Atom current(JsonParser p) throws IOException {
        if (p.currentToken() == null) {
            return null;
        }

        Object value;
        switch (p.currentToken()) {
            case VALUE_STRING:
                value = p.getValueAsString();
                break;
            case VALUE_NUMBER_INT:
                value = p.getValueAsInt();
                break;
            case VALUE_TRUE:
                value = true;
                break;
            case VALUE_FALSE:
                value = false;
                break;
            default:
                value = null;
        }
        return new Atom(p.getTokenLocation(), p.currentToken(), p.getCurrentName(), value);
    }

    public final JsonLocation location;
    public final JsonToken token;
    public final String name;
    public final Object value;

    public Atom(JsonLocation location, JsonToken token, String name, Object value) {
        this.location = location;
        this.token = token;
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Atom{" +
                "location=" + location +
                ", token=" + token +
                ", name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
