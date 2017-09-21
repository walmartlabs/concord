package com.walmartlabs.concord.it.common;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.security.secret.SecretResource;
import com.walmartlabs.concord.server.api.security.secret.UploadSecretResponse;
import com.walmartlabs.concord.server.api.process.*;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.*;

public class ServerClient {

    /**
     * As defined in db/src/main/resources/com/walmartlabs/concord/server/db/v0.0.1.xml
     */
    protected static final String DEFAULT_API_KEY = "auBy4eDWrKWsyhiDp3AQiw";

    private String apiKey = DEFAULT_API_KEY;

    private final String baseUrl;
    private final Client client;
    private final WebTarget target;

    public ServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = createClient(this::getApiKey);
        this.target = client.target(baseUrl);
    }

    public <T> T proxy(Class<T> klass) {
        return ((ResteasyWebTarget) target).proxy(klass);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public StartProcessResponse start(Map<String, Object> input) {
        return start(null, input);
    }

    public StartProcessResponse start(String entryPoint, Map<String, Object> input) {
        return request(ProcessResource.class.getAnnotation(Path.class).value() + (entryPoint != null ? "/" + entryPoint : ""),
                input, StartProcessResponse.class);
    }

    private MultipartFormDataOutput createMDO(Map<String, Object> input) {
        MultipartFormDataOutput mdo = new MultipartFormDataOutput();
        input.forEach((k, v) -> {
            if (v instanceof InputStream || v instanceof byte[]) {
                mdo.addFormData(k, v, MediaType.APPLICATION_OCTET_STREAM_TYPE);
            } else if (v instanceof String) {
                mdo.addFormData(k, v, MediaType.TEXT_PLAIN_TYPE);
            } else {
                throw new IllegalArgumentException("Unknown input type: " + v);
            }
        });
        return mdo;
    }

    private <T> T request(String uri, Map<String, Object> input, Class<T> entityType) {
        WebTarget target = client.target(baseUrl + "/" + uri);

        MultipartFormDataOutput mdo = createMDO(input);
        GenericEntity<MultipartFormDataOutput> entity = new GenericEntity<MultipartFormDataOutput>(mdo) {
        };

        Response resp = target.request().post(Entity.entity(entity, MediaType.MULTIPART_FORM_DATA));
        if (resp.getStatus() != Status.OK.getStatusCode()) {
            resp.close();
            throw new WebApplicationException(resp);
        }

        T e = resp.readEntity(entityType);
        resp.close();
        return e;
    }

    public UploadSecretResponse addPlainSecret(String name, boolean generatePassword, String storePassword, byte[] secret) {
        return request(SecretResource.class.getAnnotation(Path.class).value() + "/plain?name=" + name + "&generatePassword=" + generatePassword,
                ImmutableMap.of("secret", secret, "storePassword", storePassword),
                UploadSecretResponse.class);
    }

    public UploadSecretResponse addUsernamePassword(String name, boolean generatePassword, String storePassword, String username, String password) {
        return request(SecretResource.class.getAnnotation(Path.class).value() + "/password?name=" + name + "&generatePassword=" + generatePassword,
                ImmutableMap.of("username", username, "password", password, "storePassword", storePassword),
                UploadSecretResponse.class);
    }

    public byte[] getLog(String logFileName) {
        WebTarget t = client.target(baseUrl + "/logs/" + logFileName);
        Response resp = t.request().get();
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());
        byte[] ab = resp.readEntity(byte[].class);
        resp.close();
        return ab;
    }

    public static ProcessEntry waitForStatus(ProcessResource processResource, UUID instanceId,
                                             ProcessStatus status, ProcessStatus... more) throws InterruptedException {
        int retries = 10;

        ProcessEntry pir;
        while (true) {
            try {
                pir = processResource.get(instanceId);
                if (status == pir.getStatus()) {
                    return pir;
                }

                for (ProcessStatus s : more) {
                    if (pir.getStatus() == s) {
                        return pir;
                    }
                }
            } catch (NotFoundException e) {
                System.out.println(String.format("waitForCompletion ['%s'] -> not found, retrying... (%s)", instanceId, retries));
                if (--retries < 0) {
                    throw e;
                }
            }

            Thread.sleep(500);
        }
    }

    public static ProcessEntry waitForChild(ProcessResource processResource,
                                            UUID parentInstanceId, ProcessKind kind,
                                            ProcessStatus status, ProcessStatus... more) throws InterruptedException {

        int retries = 10;
        while (true) {
            List<ProcessEntry> l = processResource.list(parentInstanceId);
            for (ProcessEntry e : l) {
                if (e.getKind() == kind) {
                    if (e.getStatus() == status) {
                        return e;
                    }

                    for (ProcessStatus s : more) {
                        if (e.getStatus() == s) {
                            return e;
                        }
                    }
                }
            }

            if (--retries < 0) {
                throw new IllegalStateException("Child process not found: " + kind);
            }

            Thread.sleep(1000);
        }
    }

    public static ProcessEntry waitForCompletion(ProcessResource processResource, UUID instanceId) throws InterruptedException {
        return waitForStatus(processResource, instanceId, ProcessStatus.FAILED, ProcessStatus.FINISHED);
    }

    public static void assertLog(String pattern, byte[] ab) throws IOException {
        assertEquals(1, grep(pattern, ab).size());
    }

    public static void assertLog(String pattern, int times, byte[] ab) throws IOException {
        assertEquals(times, grep(pattern, ab).size());
    }

    public static void assertLogAtLeast(String pattern, int times, byte[] ab) throws IOException {
        assertTrue(times <= grep(pattern, ab).size());
    }

    public void waitForLog(String logFileName, String pattern) throws IOException, InterruptedException {
        int retries = 5;

        while (true) {
            byte[] ab = getLog(logFileName);
            if (!grep(pattern, ab).isEmpty()) {
                break;
            }

            if (--retries < 0) {
                fail("waitForLog: " + pattern);
            }

            Thread.sleep(500);
        }
    }

    private static Client createClient(Supplier<String> k) {
        return ClientBuilder.newClient()
                .register((ClientRequestFilter) requestContext -> {
                    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
                    headers.putSingle("Authorization", k.get());
                });
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
