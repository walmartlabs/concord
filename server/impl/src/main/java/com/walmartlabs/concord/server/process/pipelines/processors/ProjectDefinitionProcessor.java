package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Named
@Singleton
public class ProjectDefinitionProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProjectDefinitionProcessor.class);

    private final ProjectLoader loader = new ProjectLoader();

    private final LogManager logManager;

    @Inject
    public ProjectDefinitionProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        try {
            ProjectDefinition pd = loader.load(workspace);
            payload = payload.putHeader(Payload.PROJECT_DEFINITION, pd);
            return chain.process(payload);
        } catch (IOException e) {
            log.warn("process ['{}'] -> project loading error: {}", instanceId, workspace, e);
            logManager.error(instanceId,"Error while loading a project file: " + workspace, e);
            throw new ProcessException(instanceId, "Error while loading a project file: " + workspace, e);
        }
    }
}
