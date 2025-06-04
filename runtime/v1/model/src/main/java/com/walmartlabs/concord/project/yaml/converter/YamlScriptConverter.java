package com.walmartlabs.concord.project.yaml.converter;

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

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlScript;
import io.takari.bpm.model.ScriptTask;

import java.util.Map;

public class YamlScriptConverter implements StepConverter<YamlScript> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlScript s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        Map<String, Object> options = s.getOptions();
        String body = (String) options.get("body");

        if(body != null) {
            c.addElement(new ScriptTask(id, ScriptTask.Type.CONTENT, s.getName(), body, true));
        } else {
            c.addElement(new ScriptTask(id, ScriptTask.Type.REFERENCE, null, s.getName(), true));
        }

        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Script"));

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
