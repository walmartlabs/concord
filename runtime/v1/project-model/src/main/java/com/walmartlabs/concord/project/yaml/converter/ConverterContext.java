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
import com.walmartlabs.concord.project.yaml.model.*;
import io.takari.bpm.model.*;
import io.takari.parc.Seq;

import java.util.*;

public class ConverterContext {

    private static final Map<Class, StepConverter> converters = new HashMap<>();

    static {
        converters.put(YamlExpressionStep.class, new YamlExpressionStepConverter());
        converters.put(YamlCall.class, new YamlCallConverter());
        converters.put(YamlCheckpoint.class, new YamlCheckpointConverter());
        converters.put(YamlReturn.class, new YamlReturnConverter());
        converters.put(YamlGroup.class, new YamlGroupConverter());
        converters.put(YamlTaskStep.class, new YamlTaskStepConverter());
        converters.put(YamlTaskShortStep.class, new YamlTaskShortStepConverter());
        converters.put(YamlIfExpr.class, new YamlIfExprConverter());
        converters.put(YamlEvent.class, new YamlEventConverter());
        converters.put(YamlScript.class, new YamlScriptConverter());
        converters.put(YamlSetVariablesStep.class, new YamlSetVariablesStepConverter());
        converters.put(YamlDockerStep.class, new YamlDockerStepConverter());
        converters.put(YamlFormCall.class, new YamlFormCallConverter());
        converters.put(YamlSwitchExpr.class, new YamlSwitchExprConverter());
        converters.put(YamlExit.class, new YamlExitConverter());
    }

    public static SourceAwareProcessDefinition convert(String name, Seq<YamlStep> steps) throws YamlConverterException {
        return convert(name, steps.toList());
    }

    public static SourceAwareProcessDefinition convert(String name, List<YamlStep> steps) throws YamlConverterException {
        ConverterContext ctx = new ConverterContext();

        Chunk c = ctx.convert(steps);
        List<AbstractElement> l = ctx.wrapAsProcess(c);
        return new SourceAwareProcessDefinition(name, l, Collections.emptyMap(), c.getSourceMap());
    }

    private int index = 0;

    public String nextId() {
        return "e_" + index++;
    }

    @SuppressWarnings("unchecked")
    public Chunk convert(YamlStep s) throws YamlConverterException {
        StepConverter c = converters.get(s.getClass());
        if (c == null) {
            throw new YamlConverterException("Unsupported step type: " + s.getClass());
        }
        return c.convert(this, s);
    }

    public Chunk convert(Seq<YamlStep> steps) throws YamlConverterException {
        return convert(steps.toList());
    }

    public Chunk convert(List<YamlStep> steps) throws YamlConverterException {
        if (steps == null || steps.isEmpty()) {
            return new Chunk();
        }

        if (steps.size() == 1) {
            return convert(steps.get(0));
        }

        Chunk c = new Chunk();

        Chunk prev = null;
        for (YamlStep s : steps) {
            Chunk curr = convert(s);

            if (prev != null) {
                connect(c, prev, curr);
            }

            c.addElements(curr.getElements());
            c.addSourceMaps(curr.getSourceMap());
            prev = curr;
        }

        if (prev != null) {
            c.addOutputs(prev.getOutputs());
        }

        return c;
    }

    public List<AbstractElement> wrapAsProcess(Chunk c) {
        List<AbstractElement> l = new ArrayList<>();

        String startId = nextId();
        l.add(new StartEvent(startId));

        if (c.isEmpty()) {
            String endId = nextId();
            l.add(new SequenceFlow(nextId(), startId, endId));
            l.add(new EndEvent(endId));
            return l;
        }

        l.add(new SequenceFlow(nextId(), startId, c.firstElement().getId()));
        l.addAll(c.getElements());

        if (!c.getOutputs().isEmpty()) {
            String endId = nextId();
            for (String src : c.getOutputs()) {
                l.add(new SequenceFlow(nextId(), src, endId));
            }
            l.add(new EndEvent(endId));
        }

        return l;
    }

    private void connect(Chunk c, Chunk a, Chunk b) {
        String dst = b.firstElement().getId();
        for (String src : a.getOutputs()) {
            c.addElement(new SequenceFlow(nextId(), src, dst));
        }
    }
}
