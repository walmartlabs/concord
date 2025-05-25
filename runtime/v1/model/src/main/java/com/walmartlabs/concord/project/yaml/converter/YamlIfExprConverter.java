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
import com.walmartlabs.concord.project.yaml.model.YamlIfExpr;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import io.takari.bpm.model.ExclusiveGateway;
import io.takari.bpm.model.SequenceFlow;
import io.takari.parc.Seq;

public class YamlIfExprConverter implements StepConverter<YamlIfExpr> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlIfExpr s) throws YamlConverterException {
        Chunk c = new Chunk();

        String gwId = ctx.nextId();
        c.addElement(new ExclusiveGateway(gwId));
        c.addSourceMap(gwId, toSourceMap(s, "Check: " + s.getExpr()));

        // "then" branch
        Chunk thenChunk = ctx.convert(s.getThenSteps());

        // connect "then" steps with the gateway
        String thenDst = thenChunk.firstElement().getId();
        c.addElement(new SequenceFlow(ctx.nextId(), gwId, thenDst, s.getExpr()));
        c.addElements(thenChunk.getElements());
        c.addOutputs(thenChunk.getOutputs());
        c.addSourceMaps(thenChunk.getSourceMap());

        // "else" branch
        applyElseBlock(ctx, c, gwId, s.getElseSteps());

        return c;
    }

    private static void applyElseBlock(ConverterContext ctx, Chunk c, String gwId, Seq<YamlStep> steps) throws YamlConverterException {
        Chunk elseChunk = steps != null ? ctx.convert(steps) : null;
        if (elseChunk == null || elseChunk.isEmpty()) {
            c.addOutput(gwId);
            return;
        }

        // connect "else" steps with the gateway
        String elseDst = elseChunk.firstElement().getId();
        c.addElement(new SequenceFlow(ctx.nextId(), gwId, elseDst));
        c.addElements(elseChunk.getElements());

        // output of the "else" branch
        c.addOutputs(elseChunk.getOutputs());

        c.addSourceMaps(elseChunk.getSourceMap());
    }
}
