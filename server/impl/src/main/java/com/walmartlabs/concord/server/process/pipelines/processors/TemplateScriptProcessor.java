package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.CycleChecker;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TemplateScriptProcessor implements PayloadProcessor {

    public static final String REQUEST_DATA_TEMPLATE_FILE_NAME = "_main.js";
    public static final String INPUT_REQUEST_DATA_KEY = "_input";

    private final ProcessLogManager logManager;
    private final ScriptEngine scriptEngine;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public TemplateScriptProcessor(ProcessLogManager logManager) {
        this.logManager = logManager;

        // workaround for:
        // Javascript array is converted in Java to an empty map #214 (https://github.com/oracle/graaljs/issues/214)
        HostAccess access = HostAccess.newBuilder(HostAccess.ALL)
                .targetTypeMapping(Value.class, Object.class, Value::hasArrayElements, v -> new LinkedList<>(v.as(List.class))).build();
        this.scriptEngine = GraalJSScriptEngine.create(null,
                Context.newBuilder("js")
                        .allowHostAccess(access));
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        // process _main.js
        Path scriptPath = workspace.resolve(REQUEST_DATA_TEMPLATE_FILE_NAME);
        if (!Files.exists(scriptPath)) {
            return chain.process(payload);
        }

        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> in = payload.getHeader(Payload.CONFIGURATION);
        Map<String, Object> out = processScript(processKey, in, scriptPath);

        Map<String, Object> merged = ConfigurationUtils.deepMerge(in, out);

        CycleChecker.CheckResult result = CycleChecker.check(INPUT_REQUEST_DATA_KEY, merged);
        if (result.isHasCycle()) {
            throw new ProcessException(processKey, "Found cycle in " + REQUEST_DATA_TEMPLATE_FILE_NAME + ": " +
                                                   result.getNode1() + " <-> " + result.getNode2());
        }
        payload = payload.putHeader(Payload.CONFIGURATION, merged);

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processScript(ProcessKey processKey, Map<String, Object> meta, Path templateMeta) {
        Object result;
        try (Reader r = new FileReader(templateMeta.toFile())) {
            Bindings b = scriptEngine.createBindings();
            b.put("polyglot.js.allowAllAccess", true);
            b.put(INPUT_REQUEST_DATA_KEY, meta != null ? meta : Collections.emptyMap());

            result = scriptEngine.eval(r, b);
            if (!(result instanceof Map)) {
                throw new ProcessException(processKey, "Invalid template result. Expected a Java Map instance, got " + result);
            }
        } catch (IOException | ScriptException e) {
            logManager.error(processKey, "Template script execution error", e);
            throw new ProcessException(processKey, "Template script execution error", e);
        }
        return (Map<String, Object>) result;
    }
}
