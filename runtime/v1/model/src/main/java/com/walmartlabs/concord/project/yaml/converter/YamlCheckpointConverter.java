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
import com.walmartlabs.concord.project.yaml.model.YamlCheckpoint;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.HashSet;
import java.util.Set;

public class YamlCheckpointConverter implements StepConverter<YamlCheckpoint> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlCheckpoint s) throws YamlConverterException {
        Chunk c = new Chunk();
        String id = ctx.nextId();

        String checkpointName = s.getName();

        Set<VariableMapping> inVars = new HashSet<>();
        inVars.add(new VariableMapping(null, checkpointName, null, "checkpointName", true));

        Set<VariableMapping> outVars = new HashSet<>();
        outVars.add(new VariableMapping("checkpointId", null, null, "checkpointId", true));
        outVars.add(new VariableMapping("checkpointName", null, null, "checkpointName", true));

        c.addElement(new ServiceTask(id, ExpressionType.DELEGATE, "${checkpoint}", inVars, outVars, true));

        c.addSourceMap(id, toSourceMap(s, "Checkpoint: " + checkpointName));
        c.addOutput(id);

        return c;
    }
}
