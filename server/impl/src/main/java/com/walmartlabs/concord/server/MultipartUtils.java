package com.walmartlabs.concord.server;

import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultipartUtils {

    private static final Pattern PART_NAME_PATTERN = Pattern.compile("name=\"(.*)\"");

    public static Map<String, Object> toMap(MultipartInput input) {
        Map<String, Object> result = new HashMap<>();

        try {
            for (InputPart p : input.getParts()) {
                String name = MultipartUtils.extractName(p);
                if (name == null || name.startsWith("/") || name.contains("..")) {
                    throw new WebApplicationException("Invalid attachment name: " + name);
                }

                if (p.getMediaType().isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                    String v = p.getBodyAsString().trim();
                    result.put(name, v);
                } else {
                    result.put(name, p.getBody(InputStream.class, null));
                }
            }
            return result;
        } catch (IOException e) {
            throw new WebApplicationException("Error parsing the request", e);
        }
    }

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

    public static boolean contains(MultipartInput input, String key) {
        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (key.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static String getString(MultipartInput input, String key) {
        try {
            for (InputPart p : input.getParts()) {
                String n = MultipartUtils.extractName(p);
                if (key.equalsIgnoreCase(n)) {
                    return p.getBodyAsString().trim();
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error parsing the request", e);
        }
        return null;
    }

    public static boolean getBoolean(MultipartInput input, String key, boolean defaultValue) {
        String s = getString(input, key);
        if (s == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }

    public static UUID getUuid(MultipartInput input, String key) {
        String s = getString(input, key);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Error parsing the request", e);
        }
    }

    public static InputStream getStream(MultipartInput input, String key) {
        try {
            for (InputPart p : input.getParts()) {
                String name = MultipartUtils.extractName(p);
                if (key.equals(name)) {
                    return p.getBody(InputStream.class, null);
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error parsing the request", e);
        }
        return null;
    }

    private MultipartUtils() {
    }
}
