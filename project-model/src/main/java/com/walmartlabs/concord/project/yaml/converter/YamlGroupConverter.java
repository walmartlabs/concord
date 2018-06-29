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
import com.walmartlabs.concord.project.yaml.model.YamlGroup;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.SubProcess;

import java.util.List;

public class YamlGroupConverter implements StepConverter<YamlGroup> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlGroup s) throws YamlConverterException {
        Chunk c = new Chunk();

        // create a subprocess
        Chunk sub = ctx.convert(s.getSteps());
        List<AbstractElement> l = ctx.wrapAsProcess(sub);

        // add the subprocess
        String id = ctx.nextId();
        c.addElement(new SubProcess(id, l));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Group of steps"));

        // keep the subprocess' source map
        c.addSourceMaps(sub.getSourceMap());

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
