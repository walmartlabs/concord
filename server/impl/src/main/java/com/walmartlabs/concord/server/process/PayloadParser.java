package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.ValidationErrorsException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PayloadParser {

    public static Payload parse(UUID instanceId, UUID parentInstanceId, Path baseDir, MultipartInput input) throws IOException {
        Map<String, Path> attachments = new HashMap<>();
        Map<String, Object> req = new HashMap<>();

        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (name == null || name.startsWith("/") || name.contains("..")) {
                throw new ProcessException(instanceId, "Invalid attachment name: " + name, Status.BAD_REQUEST);
            }

            if (p.getMediaType().isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                String v = p.getBodyAsString();
                Map<String, Object> m = ConfigurationUtils.toNested(name, v);
                req = ConfigurationUtils.deepMerge(req, m);
            } else {
                Path dst = baseDir.resolve(name);
                try (InputStream in = p.getBody(InputStream.class, null);
                     OutputStream out = Files.newOutputStream(dst)) {
                    IOUtils.copy(in, out);
                }

                attachments.put(name, dst);
            }
        }

        return new Payload(instanceId, parentInstanceId)
                .putHeader(Payload.REQUEST_DATA_MAP, req)
                .putAttachments(attachments);
    }

    public static EntryPoint parseEntryPoint(String entryPoint) {
        if (entryPoint == null) {
            return null;
        }

        String[] as = entryPoint.split(":");
        if (as.length < 1 || as.length > 3) {
            throw new ValidationErrorsException("Invalid entry point format: " + entryPoint);
        }

        String projectName = as[0].trim();

        String repositoryName = null;
        if (as.length > 1) {
            repositoryName = as[1].trim();
        }

        String flow = null;
        if (as.length > 2) {
            flow = as[2].trim();
        }

        return new EntryPoint(projectName, repositoryName, flow);
    }

    private PayloadParser() {
    }

    public static class EntryPoint implements Serializable {

        private final String projectName;
        private final String repositoryName;
        private final String flow;

        public EntryPoint(String projectName, String repositoryName, String flow) {
            this.projectName = projectName;
            this.repositoryName = repositoryName;
            this.flow = flow;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getRepositoryName() {
            return repositoryName;
        }

        public String getFlow() {
            return flow;
        }
    }
}
