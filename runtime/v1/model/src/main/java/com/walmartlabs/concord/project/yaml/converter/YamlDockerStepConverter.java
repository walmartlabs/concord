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
import com.walmartlabs.concord.project.yaml.model.YamlDockerStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class YamlDockerStepConverter implements StepConverter<YamlDockerStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlDockerStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();

        Map<String, Object> args = new HashMap<>();
        args.put("image", s.getImage());
        args.put("forcePull", s.isForcePull());
        args.put("debug", s.isDebug());
        args.put("cmd", s.getCmd());
        args.put("env", s.getEnv());
        args.put("envFile", s.getEnvFile());
        args.put("hosts", s.getHosts());
        args.put("stdout", s.getStdout());
        args.put("stderr", s.getStderr());

        Set<VariableMapping> inVars = createVarsMapping(args);

        c.addElement(new ServiceTask(id, ExpressionType.DELEGATE, "${docker}", inVars, null, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Docker: " + s.getImage()));

        return c;
    }

    private Set<VariableMapping> createVarsMapping(Map<String, Object> args) {
        Set<VariableMapping> result = new HashSet<>();
        
        args.forEach((key, value) -> {
            String sourceExpr = null;
            Object sourceValue = null;

            Object v = StepConverter.deepConvert(value);
            if (StepConverter.isExpression(v)) {
                sourceExpr = v.toString();
            } else {
                sourceValue = v;
            }

            result.add(new VariableMapping(null, sourceExpr, sourceValue, key, true));
        });

        return result;
    }
}
