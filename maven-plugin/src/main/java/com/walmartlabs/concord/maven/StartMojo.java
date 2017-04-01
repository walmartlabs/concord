package com.walmartlabs.concord.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

@Mojo(name = "start")
public class StartMojo extends AbstractMojo {

    @Parameter(required = true)
    private String baseDir;

    @Parameter(required = true)
    private List<FileEntry> files;

    @Parameter
    private List<String> excludes;

    @Parameter(defaultValue = "localhost:8001")
    private String serverAddr;

    @Parameter(required = true)
    private String apiToken;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (files == null || files.isEmpty()) {
            throw new MojoFailureException("Empty file list");
        }

        Collection<PathMatcher> excludes = new ArrayList<>();
        if (this.excludes != null) {
            FileSystem fs = FileSystems.getDefault();
            for (String ex : this.excludes) {
                try {
                    excludes.add(fs.getPathMatcher("glob:" + ex));
                } catch (IllegalArgumentException e) {
                    throw new MojoFailureException("Invalid pattern: " + ex, e);
                }
            }
        }

        Path basePath = Paths.get(baseDir);

        Path payload = createPayload(getLog(), basePath, files, excludes);
        sendPayload(getLog(), payload, serverAddr, apiToken);
    }

    private static Path createPayload(Log log, Path basePath, List<FileEntry> files, Collection<PathMatcher> excludes) throws MojoFailureException {
        try {
            Path payload = Files.createTempFile("payload", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(payload))) {
                for (FileEntry f : files) {
                    Path p = Paths.get(f.getSrc());
                    boolean absolute = p.isAbsolute();

                    Path start = basePath.resolve(p);

                    if (!Files.exists(start)) {
                        throw new MojoFailureException("File not found: " + f.getSrc());
                    }

                    Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            for (PathMatcher e : excludes) {
                                if (e.matches(file)) {
                                    return FileVisitResult.CONTINUE;
                                }
                            }

                            String n;
                            if (f.getDst() == null) {
                                n = absolute ? start.relativize(file).toString() : basePath.relativize(file).toString();
                            } else {
                                n = Paths.get(f.getDst(), start.relativize(file).toString()).toString();
                            }

                            log.debug("Payload: " + n);
                            IOUtils.zipFile(zip, file, n);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }

            log.info("Payload size: " + Files.size(payload) + " bytes");
            return payload;
        } catch (IOException e) {
            throw new MojoFailureException("Error creating a payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendPayload(Log log, Path payload, String serverAddr, String apiToken) throws MojoFailureException {
        try (CloseableHttpClient client = HttpClients.createDefault();
             InputStream in = Files.newInputStream(payload)) {

            HttpPost post = new HttpPost("http://" + serverAddr + "/api/v1/process");
            post.setHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
            post.setHeader(HttpHeaders.AUTHORIZATION, apiToken);
            post.setEntity(new InputStreamEntity(in));

            try (CloseableHttpResponse resp = client.execute(post)) {
                StatusLine status = resp.getStatusLine();
                if (status.getStatusCode() != HttpStatus.SC_OK) {
                    throw new MojoFailureException("Error sending a payload: " + status);
                }

                HttpEntity entity = resp.getEntity();
                String contentType = entity.getContentType() != null ? entity.getContentType().getValue() : null;
                if (!"application/json".equals(contentType)) {
                    throw new MojoFailureException("Unknown response type: " + contentType);
                }

                ObjectMapper om = new ObjectMapper();
                Map<String, Object> result = om.readValue(entity.getContent(), Map.class);

                log.info("Started. Instance ID: " + result.get("instanceId"));
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error sending a payload", e);
        }
    }

    public static class FileEntry implements Serializable {

        private String src;
        private String dst;

        public FileEntry() {
        }

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }

        public String getDst() {
            return dst;
        }

        public void setDst(String dst) {
            this.dst = dst;
        }

        @Override
        public String toString() {
            return "FileEntry{" +
                    "src='" + src + '\'' +
                    ", dst='" + dst + '\'' +
                    '}';
        }
    }
}
