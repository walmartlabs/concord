package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.ValidationErrorsException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class PayloadParser {

    public static Payload parse(String instanceId, Path baseDir, MultipartInput input) throws IOException {
        Map<String, Path> m = new HashMap<>();

        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);

            Path dst = baseDir.resolve(name);
            try (InputStream in = p.getBody(InputStream.class, null);
                 OutputStream out = Files.newOutputStream(dst)) {
                IOUtils.copy(in, out);
            }

            m.put(name, dst);
        }

        return new Payload(instanceId).putAttachments(m);
    }

    public static EntryPoint parseEntryPoint(String entryPoint) {
        String[] as = entryPoint.split(":");
        if (as.length < 1) {
            throw new ValidationErrorsException("Invalid entry point format: " + entryPoint);
        }

        String projectName = as[0].trim();
        String[] rest = as.length > 1 ? Arrays.copyOfRange(as, 1, as.length) : new String[0];
        return new EntryPoint(projectName, rest);
    }

    private PayloadParser() {
    }

    public static class EntryPoint implements Serializable {

        private final String projectName;
        private final String[] entryPoint;

        public EntryPoint(String projectName, String[] entryPoint) {
            this.projectName = projectName;
            this.entryPoint = entryPoint;
        }

        public String getProjectName() {
            return projectName;
        }

        public String[] getEntryPoint() {
            return entryPoint;
        }
    }
}
