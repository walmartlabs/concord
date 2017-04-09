package com.walmartlabs.concord.project.yaml;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Atom atom = (Atom) o;

        if (!location.equals(atom.location)) return false;
        if (token != atom.token) return false;
        if (name != null ? !name.equals(atom.name) : atom.name != null) return false;
        return value != null ? value.equals(atom.value) : atom.value == null;
    }

    @Override
    public int hashCode() {
        int result = location.hashCode();
        result = 31 * result + token.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
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
