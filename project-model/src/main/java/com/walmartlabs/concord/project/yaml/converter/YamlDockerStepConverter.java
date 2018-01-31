package com.walmartlabs.concord.project.yaml.converter;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlDockerStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;

import java.util.Arrays;

public class YamlDockerStepConverter implements StepConverter<YamlDockerStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlDockerStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();

        Object args = Arrays.asList(s.getImage(),
                s.isForcePull(),
                s.isDebug(),
                s.getCmd(),
                s.getEnv(),
                "${" + InternalConstants.Context.WORK_DIR_KEY + "}",
                s.getOptions());
        ELCall call = createELCall("docker", args);

        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, call.getExpression(), call.getArgs(), null, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Docker: " + s.getImage()));

        return c;
    }
}
