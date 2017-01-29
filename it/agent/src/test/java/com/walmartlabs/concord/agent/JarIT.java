package com.walmartlabs.concord.agent;

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.agent.api.JobResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.api.LogResource;
import com.walmartlabs.concord.agent.test.ErrorTest;
import com.walmartlabs.concord.agent.test.ForAFewSecondsTest;
import com.walmartlabs.concord.agent.test.LongRunningTest;
import com.walmartlabs.concord.agent.test.ResourceTest;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class JarIT {

    private Main main;
    private Client client;
    private JobResource jobResource;
    private LogResource logResource;

    @Before
    public void setUp() throws Exception {
        main = new Main();
        main.start();

        // we need multi threaded http client for out async test cases

        HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .build();

        ApacheHttpClient4Engine httpEngine = new ApacheHttpClient4Engine(httpClient);

        client = ((ResteasyClientBuilder) ClientBuilder.newBuilder())
                .httpEngine(httpEngine)
                .build();

        WebTarget t = client.target("http://localhost:8002");
        jobResource = ((ResteasyWebTarget) t).proxy(JobResource.class);
        logResource = ((ResteasyWebTarget) t).proxy(LogResource.class);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        main.stop();
    }

    @Test(timeout = 60000)
    public void testNormal() throws Exception {
        String id;
        try (InputStream in = new FileInputStream(makePayload(ResourceTest.class))) {
            id = jobResource.start(in, JobType.JAR, "test.jar");
        }

        // ---

        JobStatus s;
        while (true) {
            s = jobResource.getStatus(id);
            if (s != JobStatus.RUNNING) {
                break;
            }
            Thread.sleep(500);
        }

        assertEquals(s, JobStatus.COMPLETED);

        // ---

        Response resp = logResource.stream(id/*, "bytes=0-"*/);
        byte[] ab = resp.readEntity(byte[].class);

        assertEquals(1, grep(".*first line.*", ab).size());
        assertEquals(1, grep(".*second line.*", ab).size());
    }

    @Test(timeout = 60000)
    public void testAsyncLog() throws Exception {
        String id;
        try (InputStream in = new FileInputStream(makePayload(ForAFewSecondsTest.class))) {
            id = jobResource.start(in, JobType.JAR, "test.jar");
        }

        // ---

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thread logThread = new Thread(() -> {
            Response resp = logResource.stream(id);
            try (InputStream in = resp.readEntity(InputStream.class)) {
                ByteStreams.copy(in, baos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        logThread.start();

        // ---

        JobStatus s;
        while (true) {
            s = jobResource.getStatus(id);
            if (s != JobStatus.RUNNING) {
                break;
            }
            Thread.sleep(500);
        }

        assertEquals(s, JobStatus.COMPLETED);

        // ---

        logThread.join();

        // ---

        byte[] ab = baos.toByteArray();

        assertEquals(1, grep(".*AAA.*", ab).size());
        assertEquals(1, grep(".*BBB.*", ab).size());
    }

    @Test(timeout = 60000)
    public void testInterrupted() throws Exception {
        String id;
        try (InputStream in = new FileInputStream(makePayload(LongRunningTest.class))) {
            id = jobResource.start(in, JobType.JAR, "test.jar");
        }

        // ---

        while (true) {
            JobStatus s = jobResource.getStatus(id);
            if (s == JobStatus.RUNNING) {
                break;
            }
            Thread.sleep(500);
        }

        // ---

        Thread.sleep(2000);

        // ---

        jobResource.cancelAll();

        // ---

        JobStatus s = jobResource.getStatus(id);
        assertEquals(s, JobStatus.CANCELLED);

        // ---

        Response resp = logResource.stream(id/*, null*/);
        byte[] ab = resp.readEntity(byte[].class);

        assertEquals(1, grep(".*working.*", ab).size());
    }

    @Test(timeout = 60000)
    public void testError() throws Exception {
        String id;
        try (InputStream in = new FileInputStream(makePayload(ErrorTest.class))) {
            id = jobResource.start(in, JobType.JAR, "test.jar");
        }

        // ---

        while (true) {
            JobStatus s = jobResource.getStatus(id);
            if (s == JobStatus.FAILED) {
                break;
            }
            Thread.sleep(500);
        }

        // ---

        Response resp = logResource.stream(id/*, null*/);
        byte[] ab = resp.readEntity(byte[].class);

        assertEquals(1, grep(".*Kaboom.*", ab).size());
    }

    // TODO move into the common project?
    public static List<String> grep(String pattern, byte[] ab) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(ab)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    private static File makePayload(Class<?> mainClass) throws IOException, URISyntaxException {
        File payload = File.createTempFile("test", "zip");
        Path tmpDir = Files.createTempDirectory("test");


        // test resource

        File res = File.createTempFile("test", "txt");
        try (OutputStream out = new FileOutputStream(res)) {
            out.write("first line\nsecond line".getBytes());
        }

        // create runnable jar

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass.getName());

        try (FileOutputStream jarFile = new FileOutputStream(tmpDir.resolve("test.jar").toFile());
             JarOutputStream jar = new JarOutputStream(jarFile, manifest)) {
            add(jar, "test.txt", res.toURI());

            String mainPath = mainClass.getName().replace('.', '/') + ".class";
            URI mainUri = mainClass.getClassLoader().getResource(mainPath).toURI();

            add(jar, mainPath, mainUri);
        }

        // zip it

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(payload))) {
            IOUtils.zip(out, tmpDir);
        }

        return payload;
    }

    private static void add(JarOutputStream jar, String name, URI uri) throws IOException {
        File f = new File(uri);

        JarEntry e = new JarEntry(name);
        e.setTime(f.lastModified());
        e.setSize(f.length());

        jar.putNextEntry(e);

        try (InputStream src = new BufferedInputStream(new FileInputStream(f))) {
            ByteStreams.copy(src, jar);
        }

        jar.closeEntry();
    }
}
