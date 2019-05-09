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
import com.walmartlabs.concord.project.yaml.model.YamlCall;
import io.takari.bpm.model.CallActivity;
import io.takari.bpm.model.VariableMapping;

import java.util.Set;

public class YamlCallConverter implements StepConverter<YamlCall> {

    /**
     * with items:
     *         /<-----------------------/
     * -> nextItem -> callActivity -> gw ->
     *
     *
     * @param ctx
     * @param s
     * @return
     * @throws YamlConverterException
     */
    @Override
    public Chunk convert(ConverterContext ctx, YamlCall s) throws YamlConverterException {
        Set<VariableMapping> inVars = getVarMap(s.getOptions(), "in");
        Set<VariableMapping> outVars = getVarMap(s.getOptions(), "out");

        Chunk c = new Chunk();
        String id = ctx.nextId();

        String calledElement = null;
        String calledElementExpression = null;
        if (StepConverter.isExpression(s.getKey())) {
            calledElementExpression = s.getKey();
        } else {
            calledElement = s.getKey();
        }
        c.addElement(new CallActivity(id, calledElement, calledElementExpression, inVars, outVars, true));
        c.addSourceMap(id, toSourceMap(s, "Flow call: " + s.getKey()));
        c.addOutput(id);
        applyErrorBlock(ctx, c, id, s.getOptions());

        return applyWithItems(ctx, c, s.getOptions());
    }
}
