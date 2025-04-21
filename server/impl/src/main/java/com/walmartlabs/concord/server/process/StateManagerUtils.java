package com.walmartlabs.concord.server.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.sdk.Constants.Files.CONFIGURATION_FILE_NAME;

public final class StateManagerUtils {

    private static final Map<String, List<String>> STATE_FILTER = Map.of(CONFIGURATION_FILE_NAME, List.of("arguments"));
    private static final List<String> ALLOWED_EXTENSIONS = List.of("json", "yaml", "yml");

    public static InputStream stateFilter(String file, InputStream in) {
        try {
            String extension = getFileExtension(file);
            if (!ALLOWED_EXTENSIONS.contains(extension) || STATE_FILTER.get(file) == null) {
                // only filter for allowed extension files
                return in;
            }

            byte[] inputBytes = in.readAllBytes();
            if (inputBytes.length == 0) {
                return new ByteArrayInputStream(inputBytes);
            }

            Map<String, Object> map = switch (extension) {
                case "json" -> new ObjectMapper().readValue(inputBytes, Map.class);
                case "yaml", "yml" -> new ObjectMapper(new YAMLFactory()).readValue(inputBytes, Map.class);
                default -> null;
            };

            if (map == null) {
                return new ByteArrayInputStream(inputBytes);
            }

            Map<String, Object> filteredMap = STATE_FILTER.get(file).stream()
                    .filter(map::containsKey)
                    .collect(HashMap::new, (m, key) -> m.put(key, map.get(key)), HashMap::putAll);

            byte[] data = switch (extension) {
                case "json" -> new ObjectMapper().writeValueAsBytes(filteredMap);
                case "yaml", "yml" -> new ObjectMapper(new YAMLFactory()).writeValueAsBytes(filteredMap);
                default -> throw new IllegalArgumentException("Unsupported file extension: " + extension);
            };

            return new ByteArrayInputStream(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getFileExtension(String fileName) {
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        else return "";
    }

    private StateManagerUtils() {
    }
}
