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
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.exception.MandatoryFieldNotFoundException;
import com.walmartlabs.concord.runtime.v2.exception.UnknownOptionException;
import io.takari.parc.Input;
import io.takari.parc.Parser;
import io.takari.parc.Result;
import io.takari.parc.Seq;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.*;

public final class GrammarOptions {

    public static final Parser<Atom, SimpleOptions> simpleOptions =
            with(ImmutableSimpleOptions::builder,
                    o -> options(
                            optional("meta", mapVal.map(o::meta))
                    ))
                    .map(ImmutableSimpleOptions.Builder::build);

    public static final Parser<Atom, SimpleOptions> namedOptions =
            with(ImmutableSimpleOptions::builder,
                    o -> options(
                            optional("meta", mapVal.map(o::meta)),
                            optional("name", stringVal.map(v -> o.putMeta(Constants.SEGMENT_NAME, v)))
                    ))
                    .map(ImmutableSimpleOptions.Builder::build);

    public static <O> Option<O> any(BiFunction<Atom, String, Parser<Atom, ? extends O>> f) {
        return Option.of(f);
    }

    public static <O> Option<O> optional(String name, Parser<Atom, O> p) {
        return Option.of(name, false, p);
    }

    public static <O> Option<O> mandatory(String name, Parser<Atom, O> p) {
        return Option.of(name, true, p);
    }

    @SafeVarargs
    public static <O> Parser<Atom, List<O>> options(Option<? extends O>... os) {
        return options(Arrays.asList(os));
    }

    public static <O> Parser<Atom, List<O>> options(List<Option<? extends O>> options) {
        return in -> {
            // TODO: skip check if no mandatory options
            // Check mandatory options
            Result<Atom, Set<String>> yamlOptions = allOptionKeys.apply(in);
            Set<String> opts = Collections.emptySet();
            if (yamlOptions.isSuccess()) {
                opts = yamlOptions.toSuccess().getResult();
            }
            assertMandatoryOptions(opts, options);

            Result<Atom, List<O>> rp = many(_choice(options)).map(Seq::toList).apply(in);
            if (rp.isFailure()) {
                return fail(in, null);
            }

            // handle unparsed options
            Input<Atom> rest = rp.getRest();
            Result<Atom, List<KV<String, YamlValue>>> rp2 = unparsedOptionsVal.apply(rest);
            if (rp2.isFailure()) { // no unparsed options
                return rp.cast();
            }

            List<String> expectedOptions = options.stream().map(Option::name).filter(Objects::nonNull).sorted().collect(Collectors.toList());

            List<KV<String, YamlValue>> unparsedOptions = rp2.toSuccess().getResult();

            // all options after unparsed are marked as unknown, so cleanup
            if (unparsedOptions.size() > 1) {
                unparsedOptions = unparsedOptions.stream().filter(u -> !expectedOptions.contains(u.getKey())).collect(Collectors.toList());
            }

            throw UnknownOptionException.builder()
                    .location(unparsedOptions.get(0).getValue().getLocation())
                    .unknown(unparsedOptions.stream()
                            .map(kv -> UnknownOption.of(kv.getKey(), kv.getValue().getType(), kv.getValue().getLocation()))
                            .collect(Collectors.toList()))
                    .expected(expectedOptions)
                    .build();
        };
    }

    private static <O> void assertMandatoryOptions(Set<String> yamlOptions, List<Option<? extends O>> options) {
        List<String> notFoundMandatoryOptions = new ArrayList<>();
        for (Option<? extends O> o : options) {
            if (o.mandatory() && !yamlOptions.contains(o.name())) {
                notFoundMandatoryOptions.add(o.name());
            }
        }

        if (!notFoundMandatoryOptions.isEmpty()) {
            throw new MandatoryFieldNotFoundException(notFoundMandatoryOptions);
        }
    }

    @Value.Immutable
    public interface Option<O> {

        @Nullable
        String name();

        @Value.Default
        default boolean mandatory() {
            return false;
        }

        @Nullable
        Parser<Atom, ? extends O> parser();

        @Nullable
        BiFunction<Atom, String, ? extends Parser<Atom, ? extends O>> anyOptionFunction();

        static <O> Option<O> of(String name, boolean mandatory, Parser<Atom, O> parser) {
            return ImmutableOption.<O>builder()
                    .name(name)
                    .mandatory(mandatory)
                    .parser(parser)
                    .build();
        }

        static <O> Option<O> of(BiFunction<Atom, String, Parser<Atom, ? extends O>> anyOptionFunction) {
            return ImmutableOption.<O>builder()
                    .anyOptionFunction(anyOptionFunction)
                    .build();
        }
    }

    private static <O> Parser<Atom, O> _choice(List<Option<? extends O>> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Empty options");
        }

        return in -> {
            for (Option<? extends O> o : options) {
                if (o.parser() == null) {
                    continue;
                }
                Result<Atom, ? extends O> rp = satisfyField(o.name(), atom -> o.parser()).apply(in);
                if (rp.isSuccess()) {
                    return rp.cast();
                }
            }

            BiFunction<Atom, String, ? extends Parser<Atom, ? extends O>> anyFunction = options.stream()
                    .map(Option::anyOptionFunction)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (anyFunction != null) {
                Result<Atom, ? extends O> rp = satisfyToken(JsonToken.FIELD_NAME).bind(a -> {
                    Parser<Atom, ? extends O> result = anyFunction.apply(a, a.name);
                    return result;
                }).apply(in);

                if (rp.isSuccess()) {
                    return rp.cast();
                }
            }

            return fail(in, null);
        };
    }

    private static final Parser<Atom, KV<String, YamlValue>> fieldValue =
            testToken(JsonToken.FIELD_NAME).bind(a -> satisfyToken(JsonToken.FIELD_NAME).then(
                    value.map(v -> new KV<>(a.name, v))));

    private static final Parser<Atom, List<KV<String, YamlValue>>> unparsedOptionsVal =
            many1(fieldValue).map(Seq::toList);

    private static final Parser<Atom, Set<String>> allOptionKeys =
            many1(fieldValue).map(v -> v.stream().map(KV::getKey).collect(Collectors.toSet()));

    private GrammarOptions() {
    }
}
