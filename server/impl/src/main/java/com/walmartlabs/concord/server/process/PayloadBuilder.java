package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public final class PayloadBuilder {

    private static final String INPUT_ARCHIVE_NAME = "_input.zip";

    private Payload payload;

    public PayloadBuilder(UUID instanceId) {
        this(instanceId, null);
    }

    public PayloadBuilder(UUID instanceId, UUID parentInstanceId) {
        this.payload = new Payload(instanceId, parentInstanceId);
    }

    public PayloadBuilder kind(ProcessKind kind) {
        payload = payload.putHeader(Payload.PROCESS_KIND, kind);
        return this;
    }

    public PayloadBuilder apply(Function<PayloadBuilder, PayloadBuilder> f) {
        return f.apply(this);
    }

    public PayloadBuilder with(MultipartInput input) throws IOException {
        Map<String, Path> attachments = payload.getAttachments();
        attachments = new HashMap<>(attachments != null ? attachments : Collections.emptyMap());

        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        cfg = new HashMap<>(cfg != null ? cfg : Collections.emptyMap());

        UUID instanceId = payload.getInstanceId();
        Path baseDir = ensureBaseDir();

        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (name == null || name.startsWith("/") || name.contains("..")) {
                throw new ProcessException(instanceId, "Invalid attachment name: " + name, Response.Status.BAD_REQUEST);
            }

            if (p.getMediaType().isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                String v = p.getBodyAsString();
                Map<String, Object> m = ConfigurationUtils.toNested(name, v);
                cfg = ConfigurationUtils.deepMerge(cfg, m);
            } else {
                Path dst = baseDir.resolve(name);
                try (InputStream in = p.getBody(InputStream.class, null);
                     OutputStream out = Files.newOutputStream(dst)) {
                    IOUtils.copy(in, out);
                }

                attachments.put(name, dst);
            }
        }

        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, cfg)
                .putAttachments(attachments);

        return this;
    }

    public PayloadBuilder workspace(Path workDir) {
        payload = payload.putHeader(Payload.WORKSPACE_DIR, workDir);
        return this;
    }

    public PayloadBuilder workspace(InputStream in) throws IOException {
        Path baseDir = ensureBaseDir();

        Path archive = baseDir.resolve(INPUT_ARCHIVE_NAME);
        Files.copy(in, archive);

        payload = payload.putAttachment(Payload.WORKSPACE_ARCHIVE, archive);

        return this;
    }

    public PayloadBuilder configuration(Map<String, Object> cfg) {
        Map<String, Object> prev = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (prev != null) {
            cfg = ConfigurationUtils.deepMerge(prev, cfg);
        }
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, cfg);
        return this;
    }

    public PayloadBuilder initiator(String initiator) {
        if (initiator != null) {
            payload = payload.putHeader(Payload.INITIATOR, initiator);
        }
        return this;
    }

    public PayloadBuilder outExpressions(String[] out) {
        if (out != null && out.length != 0) {
            payload = payload.putHeader(Payload.OUT_EXPRESSIONS, new HashSet<>(Arrays.asList(out)));
        }
        return this;
    }

    public PayloadBuilder organization(UUID orgId) {
        if (orgId != null) {
            payload = payload.putHeader(Payload.ORGANIZATION_ID, orgId);
        }
        return this;
    }

    public PayloadBuilder project(UUID projectId) {
        if (projectId != null) {
            payload = payload.putHeader(Payload.PROJECT_ID, projectId);
        }
        return this;
    }

    public PayloadBuilder repository(UUID repoId) {
        if (repoId != null) {
            payload = payload.putHeader(Payload.REPOSITORY_ID, repoId);
        }
        return this;
    }

    public PayloadBuilder entryPoint(String entryPoint) {
        if (entryPoint != null) {
            payload = payload.putHeader(Payload.ENTRY_POINT, entryPoint);
        }
        return this;
    }

    public PayloadBuilder resumeEventName(String eventName) {
        payload = payload.putHeader(Payload.RESUME_EVENT_NAME, eventName);
        return this;
    }

    private Path ensureBaseDir() throws IOException {
        Path baseDir = payload.getHeader(Payload.BASE_DIR);
        if (baseDir == null) {
            baseDir = Files.createTempDirectory("payload");
            payload.putHeader(Payload.BASE_DIR, baseDir);
        }
        return baseDir;
    }

    private Path ensureWorkDir() throws IOException {
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workDir == null) {
            Path baseDir = ensureBaseDir();

            workDir = baseDir.resolve("workspace");
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }

            payload = payload.putHeader(Payload.WORKSPACE_DIR, workDir);
        }

        return workDir;
    }

    public Payload build() throws IOException {
        ensureWorkDir();
        return payload;
    }
}
