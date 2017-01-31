package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PayloadParser {

    private static final Pattern PART_NAME_PATTERN = Pattern.compile(".*name=\"(.*)\".*");

    public static Payload parse(String instanceId, Path baseDir, MultipartInput input) throws IOException {
        Map<String, Path> m = new HashMap<>();

        for (InputPart p : input.getParts()) {
            String name = extractName(p);

            Path dst = baseDir.resolve(name);
            try (InputStream in = p.getBody(InputStream.class, null);
                 OutputStream out = Files.newOutputStream(dst)) {
                IOUtils.copy(in, out);
            }

            m.put(name, dst);
        }

        return new Payload(instanceId).putAttachments(m);
    }

    private static String extractName(InputPart p) {
        MultivaluedMap<String, String> headers = p.getHeaders();
        if (headers == null) {
            return null;
        }

        String s = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (s == null) {
            return null;
        }

        Matcher m = PART_NAME_PATTERN.matcher(s);
        if (!m.matches()) {
            return null;
        }

        return m.group(1);
    }

    private PayloadParser() {
    }
}
