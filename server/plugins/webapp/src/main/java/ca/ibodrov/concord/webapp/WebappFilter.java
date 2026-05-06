package ca.ibodrov.concord.webapp;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * @implNote The filter should be applied after all other filters.
 */
@WebFilter("/*")
@Priority(Integer.MAX_VALUE - 1000)
public class WebappFilter extends HttpFilter {

    private static final String CACHE_CONTROL_IMMUTABLE = "public, max-age=2592000, immutable";

    private final WebappCollection webapps;
    private final ExcludedPrefixes excludedPrefixes;

    @Inject
    public WebappFilter(ExcludedPrefixes excludedPrefixes) {
        this.webapps = loadWebapps();
        this.excludedPrefixes = requireNonNull(excludedPrefixes);
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        var uri = req.getRequestURI();
        if (excludedPrefixes.matches(uri)) {
            chain.doFilter(req, resp);
            return;
        }

        var webapp = webapps.stream().filter(w -> uri.startsWith(w.path())).findFirst();
        if (webapp.isPresent()) {
            doWebappRequest(webapp.get(), req, resp);
        } else {
            chain.doFilter(req, resp);
        }
    }

    private void doWebappRequest(Webapp webapp, HttpServletRequest req, HttpServletResponse resp) {
        var method = req.getMethod();
        var isGet = "GET".equals(method);
        var isHead = "HEAD".equals(method);
        if (!isGet && !isHead) {
            resp.setHeader("Allow", "GET, HEAD");
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var resolvedResource = resolveResource(webapp, req.getRequestURI());
        var resource = resolvedResource.resource();

        resp.setHeader("Content-Type", resource.contentType());
        if (resolvedResource.immutableCacheControl() && !resp.containsHeader("Cache-Control")) {
            resp.setHeader("Cache-Control", CACHE_CONTROL_IMMUTABLE);
        }
        resp.setHeader("ETag", resource.eTag());

        if (matchesIfNoneMatch(req.getHeader("If-None-Match"), resource.eTag())) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        var content = webapp.getContent(resource);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentLength(content.length);

        if (isHead) {
            return;
        }

        try {
            resp.getOutputStream().write(content);
        } catch (IOException e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }

    private static ResolvedResource resolveResource(Webapp webapp, String requestUri) {
        var path = requestUri;

        if (!path.startsWith(webapp.path())) {
            throw new RuntimeException("Unexpected path: " + path);
        }

        path = path.length() > webapp.path().length() ? path.substring(webapp.path().length()) : "";

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }

        var resource = webapp.resources().get(path);
        if (resource != null) {
            return new ResolvedResource(resource, !path.equals(webapp.indexHtmlRelativePath()));
        }

        return new ResolvedResource(webapp.resources().get(webapp.indexHtmlRelativePath()), false);
    }

    private record ResolvedResource(StaticResource resource, boolean immutableCacheControl) {
    }

    private static boolean matchesIfNoneMatch(String ifNoneMatch, String eTag) {
        if (ifNoneMatch == null) {
            return false;
        }

        return splitEntityTags(ifNoneMatch).stream()
                .anyMatch(candidate -> "*".equals(candidate) || weakCompareEntityTags(candidate, eTag));
    }

    private static boolean weakCompareEntityTags(String a, String b) {
        var aTag = toStrongEntityTag(a);
        var bTag = toStrongEntityTag(b);
        return aTag.isPresent() && aTag.equals(bTag);
    }

    private static Optional<String> toStrongEntityTag(String eTag) {
        var result = eTag.trim();
        if (result.length() > 2 && (result.charAt(0) == 'W' || result.charAt(0) == 'w') && result.charAt(1) == '/') {
            result = result.substring(2).trim();
        }

        if (result.length() < 2 || result.charAt(0) != '"' || result.charAt(result.length() - 1) != '"') {
            return Optional.empty();
        }

        return Optional.of(result);
    }

    private static List<String> splitEntityTags(String header) {
        var result = new ArrayList<String>();
        var quoted = false;
        var escaped = false;
        var start = 0;

        for (var i = 0; i < header.length(); i++) {
            var c = header.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }

            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                quoted = !quoted;
                continue;
            }

            if (c == ',' && !quoted) {
                addEntityTag(result, header.substring(start, i));
                start = i + 1;
            }
        }

        addEntityTag(result, header.substring(start));
        return result;
    }

    private static void addEntityTag(List<String> result, String value) {
        var item = value.trim();
        if (!item.isEmpty()) {
            result.add(item);
        }
    }

    private static WebappCollection loadWebapps() {
        var result = new ArrayList<Webapp>();
        var classLoader = WebappPluginModule.class.getClassLoader();
        try {
            classLoader.getResources("META-INF/concord/webapp.properties")
                    .asIterator()
                    .forEachRemaining(source -> {
                        var webapp = new Webapp(source);
                        result.add(webapp);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new WebappCollection(result);
    }

    private static final class Webapp {

        private static final int CACHE_SIZE = 1000;

        private final String path;
        private final Map<String, StaticResource> resources;
        private final String indexHtmlRelativePath;
        private final LoadingCache<String, byte[]> contentCache;

        private Webapp(URL propertiesSource) {
            var props = new Properties();
            try (var in = propertiesSource.openStream()) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            this.path = assertString(propertiesSource, props, "path");
            this.resources = loadResources(assertString(propertiesSource, props, "checksumsFileResourcePath"));
            this.indexHtmlRelativePath = assertString(propertiesSource, props, "indexHtmlRelativePath");

            var resourceRoot = assertString(propertiesSource, props, "resourceRoot");
            this.contentCache = CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(new CacheLoader<>() {
                        @Override
                        public byte[] load(String key) throws Exception {
                            var filePath = resourceRoot + key;
                            try (var in = WebappFilter.class.getClassLoader().getResourceAsStream(filePath)) {
                                if (in == null) {
                                    throw new IllegalStateException("Resource not found: " + filePath);
                                }
                                return in.readAllBytes();
                            }
                        }
                    });
        }

        public String path() {
            return path;
        }

        public Map<String, StaticResource> resources() {
            return resources;
        }

        public String indexHtmlRelativePath() {
            return indexHtmlRelativePath;
        }

        public byte[] getContent(StaticResource resource) {
            return contentCache.getUnchecked(resource.path());
        }
    }

    private static class WebappCollection {

        // must be sorted, longest prefixes first
        private final List<Webapp> webapps;

        public WebappCollection(Collection<Webapp> webapps) {
            this.webapps = webapps.stream()
                    .sorted(comparing(Webapp::path).reversed())
                    .toList();
        }

        public Stream<Webapp> stream() {
            return webapps.stream();
        }
    }

    private static Map<String, StaticResource> loadResources(String file) {
        var resources = ImmutableMap.<String, StaticResource>builder();

        var cl = WebappFilter.class.getClassLoader();
        try (var in = cl.getResourceAsStream(file)) {
            if (in == null) {
                throw new RuntimeException(file + " file not found. Classpath or build issues?");
            }

            try (var reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.startsWith("#")) {
                        continue;
                    }

                    var items = line.split(",");
                    if (items.length != 2) {
                        throw new RuntimeException(file + " file, invalid line: " + line);
                    }

                    var path = items[0];
                    var eTag = toEntityTag(items[1]);
                    var contentType = getContentType(path)
                            .orElseThrow(() -> new RuntimeException("Can't determine Content-Type for " + path));

                    resources.put(path, new StaticResource(path, contentType, eTag));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return resources.build();
    }

    private static String toEntityTag(String value) {
        var result = value.trim();
        if (result.isEmpty() || result.indexOf('"') >= 0) {
            throw new RuntimeException("Invalid ETag value: " + value);
        }
        return "\"" + result + "\"";
    }

    private static String assertString(URL source, Properties props, String key) {
        var value = props.getProperty(key);
        if (value == null) {
            throw new RuntimeException("Missing required property: %s (in %s)".formatted(key, source));
        }
        return value;
    }

    private static Optional<String> getContentType(String fileName) {
        var extIdx = fileName.lastIndexOf('.');
        if (extIdx < 2 || extIdx >= fileName.length() - 1) {
            return Optional.empty();
        }
        var ext = fileName.substring(extIdx + 1);
        return Optional.ofNullable(switch (ext) {
            case "css" -> "text/css";
            case "eot" -> "application/vnd.ms-fontobject";
            case "gif" -> "image/gif";
            case "html" -> "text/html";
            case "jpg", "jpeg" -> "image/jpeg";
            case "js" -> "text/javascript";
            case "json" -> "text/json";
            case "map" -> "application/json";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "ttf" -> "font/ttf";
            case "txt" -> "text/plain";
            case "webp" -> "image/webp";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> null;
        });
    }
}
