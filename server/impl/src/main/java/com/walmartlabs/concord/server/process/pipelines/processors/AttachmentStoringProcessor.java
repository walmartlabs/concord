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

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class AttachmentStoringProcessor implements PayloadProcessor {

    /**
     * Attachments used by the server. They should not be added to the workspace.
     */
    private static final Set<String> SYSTEM_ATTACHMENT_NAMES = ImmutableSet.of(Payload.WORKSPACE_ARCHIVE.name(),
            ConfigurationProcessor.REQUEST_ATTACHMENT_KEY.name());

    private static final Pattern RAW_PAYLOAD_PATTERN = Pattern.compile("^(concord|flows)[/.](.*)(yml|yaml)$");

    private final ProjectDao projectDao;

    @Inject
    public AttachmentStoringProcessor(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Path> m = payload.getAttachments();
        if (m == null || m.isEmpty()) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        for (Map.Entry<String, Path> entry : m.entrySet()) {
            String name = entry.getKey();
            if (SYSTEM_ATTACHMENT_NAMES.contains(name)) {
                continue;
            }

            UUID projectId = payload.getHeader(Payload.PROJECT_ID);
            if (hasRawPayload(name) && !canAcceptRawPayload(projectId)) {
                throw new ProcessException(payload.getProcessKey(), "Project is not accepting flows in attachments (" + name + "). Check the \"Allow payload archives\" setting.");
            }

            Path src = entry.getValue();
            Path dst = workspace.resolve(name);

            try {
                Files.createDirectories(dst.getParent());
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                payload = payload.removeAttachment(name);
            } catch (IOException e) {
                throw new ProcessException(payload.getProcessKey(), "Error while copying an attachment: " + src, e);
            }
        }

        return chain.process(payload);
    }

    private boolean hasRawPayload(String name) {
        return RAW_PAYLOAD_PATTERN.matcher(name).matches();
    }

    private boolean canAcceptRawPayload(UUID projectId) {
        if (projectId == null) {
            return true;
        }

        ProjectEntry project = projectDao.get(projectId);
        return project.getRawPayloadMode() != RawPayloadMode.DISABLED;
    }
}
