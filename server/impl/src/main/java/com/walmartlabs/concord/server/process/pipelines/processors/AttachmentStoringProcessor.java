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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

@Named
public class AttachmentStoringProcessor implements PayloadProcessor {

    /**
     * Attachments used by the server. They should not be added to the workspace.
     */
    public static final Set<String> SYSTEM_ATTACHMENT_NAMES = ImmutableSet.of(Payload.WORKSPACE_ARCHIVE.name(),
            RequestDataMergingProcessor.REQUEST_ATTACHMENT_KEY.name());

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

            Path src = entry.getValue();
            Path dst = workspace.resolve(name);

            try {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                payload = payload.removeAttachment(name);
            } catch (IOException e) {
                throw new ProcessException(payload.getInstanceId(), "Error while copying an attachment: " + src, e);
            }
        }

        return chain.process(payload);
    }
}
