package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.exception.InvalidFieldDefinitionException;
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueTypeException;
import com.walmartlabs.concord.runtime.v2.exception.YamlProcessingException;
import io.takari.parc.Parser;
import io.takari.parc.Result;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.value;
import static io.takari.parc.Combinators.*;

public final class GrammarMisc {

    public static Parser<Atom, Atom> satisfyToken(JsonToken t) {
        return satisfy((Atom a) -> a.token == t);
    }

    public static Parser<Atom, Atom> satisfyField(String name) {
        return satisfy((Atom a) -> name.equals(a.name));
    }

    public static <O> Parser<Atom, O> betweenTokens(JsonToken start, JsonToken end, Parser<Atom, O> p) {
        return between(satisfyToken(start), satisfyToken(end), p);
    }

    public static <Atom> Parser<Atom, Atom> test(Predicate<Atom> p) {
        return in -> {
            if (in.end()) {
                return fail(in, "EOF");
            }

            Atom val = in.first();
            if (p.test(val)) {
                return ok(val, in);
            }

            return fail(in, null);
        };
    }

    public static Parser<Atom, Atom> testToken(JsonToken t) {
        return test(a -> a.token == t);
    }

    public static Parser<Atom, Atom> testField(String name) {
        return test(a -> name.equals(a.name));
    }

    public static <O, X> Parser<Atom, O> with(Supplier<O> s, Function<O, Parser<Atom, X>> p) {
        return in -> {
            O o = s.get();
            Result<Atom, X> rp = p.apply(o).apply(in);
            if (rp.isSuccess()) {
                return ok(o, rp.getRest());
            }
            return rp.cast();
        };
    }

    public static <O> Parser<Atom, O> invalidValueTypeError(YamlValueType<O> type) {
        return value.map(v -> {
            throw InvalidValueTypeException.builder()
                    .expected(type)
                    .actual(v.getType())
                    .location(v.getLocation())
                    .build();
        });
    }

    public static <O> Parser<Atom, O> orError(Parser<Atom, O> p, YamlValueType<O> type) {
        return or(p, invalidValueTypeError(type));
    }

    public static <O> Parser<Atom, O> field(String name, Function<Atom, Parser<Atom, O>> f) {
        return testField(name).bind(a -> in -> {
            try {
                return f.apply(a).apply(in);
            } catch (YamlProcessingException e) {
                throw new InvalidFieldDefinitionException(name, a.location, e);
            }
        });
    }

    public static <O> Parser<Atom, O> satisfyField(String name, Function<Atom, Parser<Atom, O>> f) {
        return satisfyField(name).bind(a -> in -> {
            try {
                return f.apply(a).apply(in);
            } catch (YamlProcessingException e) {
                throw new InvalidFieldDefinitionException(name, a.location, e);
            }
        });
    }

    public static <O> Parser<Atom, O> satisfyField(String name, YamlValueType<O> valueType, Function<Atom, Parser<Atom, O>> f) {
        return satisfyField(name).bind(a -> in -> {
            try {
                Result<Atom, O> rp = f.apply(a).apply(in);
                if (rp.isSuccess()) {
                    return rp.cast();
                }
                return invalidValueTypeError(valueType).apply(in).cast();
            } catch (YamlProcessingException e) {
                throw new InvalidFieldDefinitionException(name, a.location, e);
            }
        });
    }

    public static <O> Parser<Atom, O> satisfyAnyField(YamlValueType<O> valueType, Function<Atom, Parser<Atom, O>> f) {
        return satisfyToken(JsonToken.FIELD_NAME).bind(a -> in -> {
            try {
                Result<Atom, O> rp = f.apply(a).apply(in);
                if (rp.isSuccess()) {
                    return rp.cast();
                }
                return invalidValueTypeError(valueType).apply(in).cast();
            } catch (YamlProcessingException e) {
                throw new InvalidFieldDefinitionException(a.name, a.location, e);
            }
        });
    }

    private GrammarMisc() {
    }
}
