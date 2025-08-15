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
import com.walmartlabs.concord.project.yaml.model.YamlExpressionStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class YamlExpressionStepConverter implements StepConverter<YamlExpressionStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlExpressionStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        Set<VariableMapping> outVars = null;
        Map<String, Object> opts = s.getOptions();
        if (opts != null) {
            String out = (String) opts.get("out");
            if (out != null) {
                outVars = Collections.singleton(new VariableMapping(ServiceTask.EXPRESSION_RESULT_VAR, null, out));
            }
        }

        String id = ctx.nextId();
        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, s.getExpr(), null, outVars, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Expression: " + s.getExpr()));

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
