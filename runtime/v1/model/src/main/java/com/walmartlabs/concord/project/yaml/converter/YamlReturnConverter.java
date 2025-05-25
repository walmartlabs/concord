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
import com.walmartlabs.concord.project.yaml.model.YamlReturn;
import io.takari.bpm.model.EndEvent;

public class YamlReturnConverter implements StepConverter<YamlReturn> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlReturn s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        c.addElement(new EndEvent(id, s.getErrorCode()));
        c.addSourceMap(id, toSourceMap(s, "Return from a flow (code: " + s.getErrorCode() + ")"));

        // skip adding an output, it should be the last element of a branch

        return c;
    }
}
