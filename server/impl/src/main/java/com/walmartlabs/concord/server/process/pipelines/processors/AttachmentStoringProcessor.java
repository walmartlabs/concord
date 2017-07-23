package com.walmartlabs.concord.server.process.pipelines.processors;

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
