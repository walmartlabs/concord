package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.model.Location;

import java.io.IOException;
import java.io.Serializable;

public class Atom implements Serializable {

    private static final long serialVersionUID = 1L;

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
            case VALUE_NUMBER_FLOAT:
                value = p.getValueAsDouble();
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
        return new Atom(toLocation(p.currentTokenLocation()), p.currentToken(), p.getCurrentName(), value);
    }

    public final Location location;
    public final JsonToken token;
    public final String name;
    public final Object value;

    public Atom(Location location, JsonToken token, String name, Object value) {
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

    private static Location toLocation(JsonLocation tokenLocation) {
        return Location.builder()
                .lineNum(tokenLocation.getLineNr())
                .column(tokenLocation.getColumnNr())
                .fileName(ThreadLocalFileName.get())
                .build();
    }
}
