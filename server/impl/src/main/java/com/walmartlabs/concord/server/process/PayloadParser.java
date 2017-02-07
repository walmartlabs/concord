package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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


    private PayloadParser() {
    }
}
