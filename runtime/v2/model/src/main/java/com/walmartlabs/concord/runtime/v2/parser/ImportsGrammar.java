package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.imports.*;
import com.walmartlabs.concord.runtime.v2.exception.UnknownOptionException;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.util.Arrays;
import java.util.Collections;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.regexpArrayVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.stringVal;
import static io.takari.parc.Combinators.choice;
import static io.takari.parc.Combinators.many1;

public final class ImportsGrammar {

    private static final Parser<Atom, Import.SecretDefinition> secret =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableSecretDefinition::builder,
                            o -> options(
                                    optional("org", stringVal.map(o::org)),
                                    mandatory("name", stringVal.map(o::name)),
                                    optional("password", stringVal.map(o::password))))
                            .map(ImmutableSecretDefinition.Builder::build));

    private static final Parser<Atom, Import.SecretDefinition> secretVal =
            orError(secret, YamlValueType.IMPORT_SECRET);

    private static final Parser<Atom, Import.GitDefinition> gitImport =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableGitDefinition::builder,
                            o -> options(
                                    optional("name", stringVal.map(o::name)),
                                    optional("url", stringVal.map(o::url)),
                                    optional("version", stringVal.map(o::version)),
                                    optional("path", stringVal.map(o::path)),
                                    optional("dest", stringVal.map(o::dest)),
                                    optional("exclude", regexpArrayVal.map(o::exclude)),
                                    optional("secret", secretVal.map(o::secret))))
                            .map(ImmutableGitDefinition.Builder::build));

    private static final Parser<Atom, Import.MvnDefinition> mvnImport =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableMvnDefinition::builder,
                            o -> options(
                                    mandatory("url", stringVal.map(o::url)),
                                    optional("dest", stringVal.map(o::dest))))
                            .map(ImmutableMvnDefinition.Builder::build));

    private static final Parser<Atom, Import.DirectoryDefinition> dirImport =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableDirectoryDefinition::builder,
                            o -> options(
                                    mandatory("src", stringVal.map(o::src)),
                                    optional("dest", stringVal.map(o::dest))))
                            .map(ImmutableDirectoryDefinition.Builder::build));

    private static final Parser<Atom, Import.GitDefinition> gitImportVal =
            orError(gitImport, YamlValueType.GIT_IMPORT);

    private static final Parser<Atom, Import.MvnDefinition> mvnImportVal =
            orError(mvnImport, YamlValueType.MVN_IMPORT);

    private static final Parser<Atom, Import.DirectoryDefinition> dirImportVal =
            orError(dirImport, YamlValueType.DIR_IMPORT);

    private static final Parser<Atom, Import> importDef =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    choice(
                            satisfyField("git", atom -> gitImportVal),
                            satisfyField("dir", atom -> dirImportVal),
                            choice(satisfyField("mvn", atom -> mvnImportVal),
                                    satisfyToken(JsonToken.FIELD_NAME).bind(a -> {
                                        throw UnknownOptionException.builder()
                                                .location(a.location)
                                                .unknown(Collections.singletonList(UnknownOption.of(a.name, null, a.location)))
                                                .expected(Arrays.asList("git", "mvn"))
                                                .build();
                                    }))));

    private static final Parser<Atom, Import> importVal =
            orError(importDef, YamlValueType.IMPORT);

    private static final Parser<Atom, Imports> imports =
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                    many1(importVal).map(Seq::toList))
                    .map(Imports::of);

    public static final Parser<Atom, Imports> importsVal =
            orError(imports, YamlValueType.IMPORTS);

    private ImportsGrammar() {
    }
}
