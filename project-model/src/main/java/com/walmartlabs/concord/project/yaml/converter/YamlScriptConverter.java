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

public class YamlScriptConverter implements StepConverter<YamlScript> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlScript s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        switch (s.getType()) {
            case CONTENT: {
                c.addElement(new ScriptTask(id, s.getType(), s.getLanguage(), s.getBody(), true));
                break;
            }
            case REFERENCE: {
                c.addElement(new ScriptTask(id, s.getType(), null, s.getBody(), true));
                break;
            }
            default:
                throw new YamlConverterException("Unsupported script task type: " + s.getType());
        }
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Script"));

        return c;
    }
}
