package com.walmartlabs.concord.server.boot.validation;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.walmartlabs.concord.server.boot.HttpServer;
import com.walmartlabs.concord.server.boot.ShiroListener;
import com.walmartlabs.concord.server.boot.filters.ShiroFilterHolder;
import com.walmartlabs.concord.server.boot.resteasy.ConcordApiDescriptor;
import com.walmartlabs.concord.server.boot.resteasy.ResteasyBootstrapListener;
import com.walmartlabs.concord.server.boot.resteasy.ResteasyModule;
import com.walmartlabs.concord.server.cfg.ServerConfiguration;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserResource;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the real HTTP bootstrap, UserResource, validation interceptor and exception mappers. */
@ResourceLock(Resources.LOCALE)
class ValidationHttpTest {

    private static final String ERRORS = "application/vnd.concord-validation-errors-v1+json";
    private static final String ACCEPT = MediaType.APPLICATION_JSON + ", " + ERRORS;
    private static final UUID USER_ID = UUID.fromString("f5a77272-efc5-4a8a-86ce-2bbecfa95eb1");

    private final ObjectMapper json = new ObjectMapper();
    private final UserManager users = mock(UserManager.class);
    private final UserDao userDao = mock(UserDao.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;
    private DefaultWebSecurityManager security;
    private Locale previousLocale;
    private ValidatorFactory factory;
    private URI base;

    @BeforeEach
    void start() throws Exception {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        var injector = Guice.createInjector(new ResteasyModule(), new ValidationModule(), binder -> {
            binder.bind(UserManager.class).toInstance(users);
            binder.bind(UserDao.class).toInstance(userDao);
            binder.bind(UserResource.class);
            binder.bind(InvalidResultResource.class);
        });
        factory = injector.getInstance(ValidatorFactory.class);
        var realm = new SimpleAccountRealm();
        realm.addAccount("admin", "password", Roles.ADMIN);
        security = new DefaultWebSecurityManager(realm);
        var shiro = new ShiroListener(security, Set.of(chains -> chains.createChain("/**", "authcBasic")));
        var cfg = mock(ServerConfiguration.class);
        when(cfg.getPort()).thenReturn(0);
        when(cfg.getSessionTimeout()).thenReturn(Duration.ofMinutes(5));
        when(cfg.getRequestHeaderSize()).thenReturn(8192);
        server = new HttpServer(cfg, Set.of(),
                Set.of(injector.getInstance(ResteasyBootstrapListener.class), shiro),
                Set.of(new ConcordApiDescriptor()), Set.of(), Set.of(), Set.of(),
                Set.of(new ShiroFilterHolder()), Set.of(), Set.of());
        server.start();
        var connector = (ServerConnector) server.getStatisticsHandler().getServer().getConnectors()[0];
        base = URI.create("http://localhost:" + connector.getLocalPort());
    }

    @AfterEach
    void stop() throws Exception {
        try {
            client.close();
            if (server != null) {
                server.stop();
            }
            if (factory != null) {
                factory.close();
            }
            if (security != null) {
                security.destroy();
            }
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void rejectsOversizedDtoBeforeCallingUserManager() throws Exception {
        var response = post(json.writeValueAsString(Map.of("username", "a".repeat(129), "userType", "LOCAL")), ACCEPT);
        assertValidationError(response, 400, "CreateUserRequest.createOrUpdate.arg0.username",
                "size must be between 0 and 128");
        verifyNoInteractions(users, userDao);
    }

    @Test
    void rejectsMissingAndNullUsername() throws Exception {
        for (var body : new String[]{"{\"userType\":\"LOCAL\"}", "{\"username\":null,\"userType\":\"LOCAL\"}"}) {
            assertValidationError(post(body, ACCEPT), 400, "CreateUserRequest.createOrUpdate.arg0.username",
                    "must not be null");
        }
        verifyNoInteractions(users, userDao);
    }

    @Test
    void acceptsMaximumLengthAndEmptyUsernameAccordingToCurrentConstraints() throws Exception {
        when(users.getId(anyString(), isNull(), eq(UserType.LOCAL))).thenReturn(Optional.empty());
        when(users.create(anyString(), isNull(), isNull(), isNull(), eq(UserType.LOCAL), isNull()))
                .thenAnswer(call -> user(call.getArgument(0)));
        for (var username : new String[]{"a".repeat(128), ""}) {
            var response = post(json.writeValueAsString(Map.of("username", username, "userType", "LOCAL")), ACCEPT);
            assertEquals(200, response.statusCode(), response.body());
            var body = json.readTree(response.body());
            assertEquals(USER_ID.toString(), body.required("id").asText());
            assertEquals(username, body.required("username").asText());
            verify(users).create(username, null, null, null, UserType.LOCAL, null);
        }
    }

    @Test
    void validatesPathParameterAndAllowsItsBoundary() throws Exception {
        assertValidationError(get("/api/v1/user/" + "a".repeat(129)), 400, "UserResource.findByUsername.arg0",
                "size must be between 0 and 128");
        verifyNoInteractions(users, userDao);

        var username = "a".repeat(128);
        when(users.getId(username, null, null)).thenReturn(Optional.of(USER_ID));
        when(userDao.get(USER_ID)).thenReturn(user(username));
        var response = get("/api/v1/user/" + username);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(username, json.readTree(response.body()).required("name").asText());
        verify(userDao).get(USER_ID);
    }

    @Test
    void omitsErrorEntityWhenValidationMediaTypeIsNotAccepted() throws Exception {
        var response = post("{}", MediaType.APPLICATION_JSON);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("", response.body());
        verifyNoInteractions(users, userDao);
    }

    @Test
    void mapsInvalidReturnValueToServerError() throws Exception {
        assertValidationError(get("/api/validation-contract/result"), 500,
                "ValidationHttpTest$InvalidResultResource.result.<return value>", "must not be null");
    }

    private HttpResponse<String> post(String body, String accept) throws Exception {
        return client.send(request("/api/v1/user", accept).header("Content-Type", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(request(path, ACCEPT).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String accept) {
        var credentials = Base64.getEncoder().encodeToString("admin:password".getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + credentials).header("Accept", accept);
    }

    private void assertValidationError(HttpResponse<String> response, int status, String id, String message) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(ERRORS, response.headers().firstValue("Content-Type").orElseThrow().split(";")[0]);
        var errors = json.readTree(response.body());
        assertTrue(errors.isArray(), response.body());
        assertEquals(1, errors.size(), response.body());
        var error = errors.get(0);
        assertEquals(2, error.size(), response.body());
        assertEquals(id, error.required("id").asText());
        assertTrue(error.required("message").isTextual());
        assertEquals(message, error.required("message").asText());
    }

    private static UserEntry user(String username) {
        return new UserEntry(USER_ID, username, null, null, Set.of(), UserType.LOCAL, null,
                Set.of(), false, null, false);
    }

    @Path("/api/validation-contract/result")
    public static class InvalidResultResource {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        @Validate
        @NotNull
        public String result() {
            return null;
        }
    }
}
