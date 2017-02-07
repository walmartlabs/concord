package com.walmartlabs.concord.server;

import org.jboss.resteasy.plugins.providers.multipart.InputPart;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultipartUtils {

    private static final Pattern PART_NAME_PATTERN = Pattern.compile("name=\"(.*)\"");

    public static String extractName(InputPart p) {
        MultivaluedMap<String, String> headers = p.getHeaders();
        if (headers == null) {
            return null;
        }

        String h = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (h == null) {
            return null;
        }

        String[] as = h.split(";");
        for (String s : as) {
            Matcher m = PART_NAME_PATTERN.matcher(s.trim());
            if (m.matches()) {
                return m.group(1);
            }
        }

        return null;
    }

    private MultipartUtils() {
    }
}
