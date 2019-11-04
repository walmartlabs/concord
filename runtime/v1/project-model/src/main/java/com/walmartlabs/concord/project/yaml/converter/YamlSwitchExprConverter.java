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

import com.walmartlabs.concord.project.yaml.KV;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import com.walmartlabs.concord.project.yaml.model.YamlSwitchExpr;
import io.takari.bpm.model.*;
import io.takari.parc.Seq;

import java.util.Collections;
import java.util.Set;

public class YamlSwitchExprConverter implements StepConverter<YamlSwitchExpr> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlSwitchExpr s) throws YamlConverterException {
        Chunk c = new Chunk();

        // task for switch expression
        String id = ctx.nextId();
        String switchExprResultVarName = "__switch_expr_result_" + id;
        Set<VariableMapping> outVars = Collections.singleton(new VariableMapping(ServiceTask.EXPRESSION_RESULT_VAR, null, switchExprResultVarName));
        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, s.getExpr(), null, outVars, true));
        c.addSourceMap(id, toSourceMap(s, "Switch expression: " + s.getExpr()));

        String gwId = ctx.nextId();
        c.addElement(new ExclusiveGateway(gwId));
        c.addElement(new SequenceFlow(ctx.nextId(), id, gwId));
        c.addSourceMap(gwId, toSourceMap(s, "Switch: " + s.getExpr()));

        Seq<YamlStep> defaultSteps = null;
        for(KV<String, Seq<YamlStep>> v : s.getCaseSteps().toList()) {
            String caseKey = v.getKey();
            if("default".equals(caseKey)) {
                defaultSteps = v.getValue();
                continue;
            }
            Chunk caseChunk = ctx.convert(v.getValue());

            String dst = caseChunk.firstElement().getId();
            c.addElement(new SequenceFlow(ctx.nextId(), gwId, dst, buildCaseExpression(caseKey, switchExprResultVarName)));
            c.addElements(caseChunk.getElements());
            c.addOutputs(caseChunk.getOutputs());
            c.addSourceMaps(caseChunk.getSourceMap());
        }

        // "default" case
        applyDefaultBlock(ctx, c, gwId, defaultSteps);

        return c;
    }

    private static String buildCaseExpression(String caseKey, String switchExprResultVarName) {
        String op;
        if(StepConverter.isExpression(caseKey)) {
            op = removeExpressionSymbols(caseKey);
        } else {
            op = "'" + caseKey + "'";
        }

        return "${" + op + " == " + switchExprResultVarName + "}";
    }

    private static String removeExpressionSymbols(String expr) {
        int i = expr.indexOf("${");
        return expr.substring(i + "${".length(), expr.indexOf("}", i));
    }

    private static void applyDefaultBlock(ConverterContext ctx, Chunk c, String gwId, Seq<YamlStep> steps) throws YamlConverterException {
        Chunk defaultChunk = steps != null ? ctx.convert(steps) : null;
        if (defaultChunk == null || defaultChunk.isEmpty()) {
            c.addOutput(gwId);
            return;
        }

        String dst = defaultChunk.firstElement().getId();
        c.addElement(new SequenceFlow(ctx.nextId(), gwId, dst));
        c.addElements(defaultChunk.getElements());

        // output of the "default" case
        c.addOutputs(defaultChunk.getOutputs());

        c.addSourceMaps(defaultChunk.getSourceMap());
    }
}
