package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.ExternalAuthToken;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.common.cfg.MappingAuthConfig;
import com.walmartlabs.concord.runtime.v2.model.GithubTriggerExclusiveMode;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.events.github.GithubTriggerProcessor;
import com.walmartlabs.concord.server.events.github.GithubUtils;
import com.walmartlabs.concord.server.events.github.Payload;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.walmartlabs.concord.common.MemoSupplier.memo;
import static com.walmartlabs.concord.server.events.github.Constants.COMMIT_ID_KEY;
import static com.walmartlabs.concord.server.events.github.Constants.EVENT_SOURCE;

/**
 * Handles external GitHub events.
 * Uses a custom authentication mechanism,
 * see {@link com.walmartlabs.concord.server.security.GithubAuthenticatingFilter}.
 * <p>
 * See also <a href="https://developer.github.com/webhooks/">developer.github.com/webhooks</a>
 */
@Path("/events/github")
@Tag(name = "GitHub Events")
public class GithubEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResource.class);

    private static final String ERROR_USER_EMAIL_LOOKUP = "Error looking up user info {}: {}";

    private final GithubConfiguration githubCfg;
    private final TriggerProcessExecutor executor;
    private final AuditLog auditLog;
    private final GithubTriggerProcessor processor;
    private final UserManager userManager;
    private final LdapManager ldapManager;
    private final TriggerEventInitiatorResolver initiatorResolver;
    private final Histogram startedProcessesPerEvent;
    private final Map<String, Long> rateLimitGauges;
    private final LoadingCache<EmailCacheKey, Optional<String>> ghUserEmailCache;

    @Inject
    public GithubEventResource(GithubConfiguration githubCfg,
                               TriggerProcessExecutor executor,
                               AuditLog auditLog,
                               GithubTriggerProcessor processor,
                               UserManager userManager,
                               LdapManager ldapManager,
                               TriggerEventInitiatorResolver initiatorResolver,
                               MetricRegistry metricRegistry,
                               AuthTokenProvider authTokenProvider,
                               ObjectMapperProvider objectMapperProvider) {

        this.githubCfg = githubCfg;
        this.executor = executor;
        this.auditLog = auditLog;
        this.processor = processor;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
        this.initiatorResolver = initiatorResolver;
        this.startedProcessesPerEvent = metricRegistry.histogram("started-processes-per-github-event");
        this.rateLimitGauges = new ConcurrentHashMap<>(githubCfg.getAuthConfigs().size());
        this.ghUserEmailCache = CacheBuilder.newBuilder()
                .expireAfterWrite(githubCfg.senderEmailCacheDuration())
                .maximumSize(githubCfg.senderEmailCacheSize())
                .concurrencyLevel(32)
                .recordStats()
                .build(new EmailCacheLoader(githubCfg, rateLimitGauges, authTokenProvider, objectMapperProvider.get()));

        for (MappingAuthConfig c : githubCfg.getAuthConfigs()) {
            Gauge<Long> rateLimitGauge = () -> rateLimitGauges.getOrDefault(c.id(), -1L);
            metricRegistry.gauge("github-rate-limit-" + c.id(), () -> rateLimitGauge);
        }
    }

    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @WithTimer
    @Operation(description = "Handles GitHub repository level events")
    @Parameter(name = "query", in = ParameterIn.QUERY,
            schema = @Schema(implementation = Map.class),
            extensions = @Extension(name = "concord", properties = @ExtensionProperty(name = "customQueryParams", value = "true")))
    public String onEvent(Map<String, Object> data,
                          @HeaderParam("X-GitHub-Delivery") String deliveryId,
                          @HeaderParam("X-GitHub-Event") String eventName,
                          @Context UriInfo uriInfo) {

        log.info("onEvent ['{}', '{}'] -> processing...", deliveryId, eventName);

        if ("ping".equalsIgnoreCase(eventName)) {
            return "ok";
        }

        if (executor.isDisabled(eventName)) {
            log.warn("event ['{}', '{}'] -> disabled", deliveryId, eventName);
            return "ok";
        }

        if (githubCfg.isLogEvents()) {
            auditLog.add(AuditObject.EXTERNAL_EVENT, AuditAction.ACCESS)
                    .field("source", EVENT_SOURCE)
                    .field("eventId", deliveryId)
                    .field("githubEvent", eventName)
                    .field("payload", data)
                    .log();
        }

        Payload payload = Payload.from(eventName, data);
        if (payload == null) {
            log.warn("event ['{}', '{}'] -> can't parse payload", deliveryId, eventName);
            return "ok";
        }

        List<GithubTriggerProcessor.Result> results = new ArrayList<>();
        processor.process(eventName, payload, uriInfo, results);

        Supplier<UserEntry> initiatorSupplier = memo(new GithubEventInitiatorSupplier(userManager, ldapManager, payload));

        int startedProcesses = 0;
        for (GithubTriggerProcessor.Result r : results) {
            Event e = Event.builder()
                    .id(deliveryId)
                    .name(EVENT_SOURCE)
                    .attributes(r.event())
                    .initiator(initiatorSupplier)
                    .build();

            List<PartialProcessKey> processes = executor.execute(e, r.triggers(), initiatorResolver, (t, cfg) -> {
                // if `useEventCommitId` is true then the process is forced to use the specified commit ID
                String commitId = MapUtils.getString(r.event(), COMMIT_ID_KEY);
                if (commitId != null && TriggerUtils.isUseEventCommitId(t)) {
                    cfg.put(Constants.Request.REPO_COMMIT_ID, commitId);
                    cfg.put(Constants.Request.REPO_BRANCH_OR_TAG, payload.getHead());
                }
                return cfg;
            }, new GithubExclusiveParamsResolver(payload));
            startedProcesses += processes.size();
        }
        startedProcessesPerEvent.update(startedProcesses);

        log.info("onEvent ['{}', '{}'] -> done, started process count: {}", deliveryId, eventName, startedProcesses);

        return "ok";
    }

    private static class GithubExclusiveParamsResolver implements TriggerProcessExecutor.TriggerExclusiveParamsResolver {

        private static final ObjectMapper objectMapper = new ObjectMapper();
        private final Payload payload;

        public GithubExclusiveParamsResolver(Payload payload) {
            this.payload = payload;
        }

        @Override
        public Map<String, Object> resolve(TriggerEntry t) {
            Map<String, Object> exclusive = TriggerUtils.getExclusive(t);
            if (exclusive.isEmpty()) {
                return exclusive;
            }

            GithubTriggerExclusiveMode e = objectMapper.convertValue(exclusive, GithubTriggerExclusiveMode.class);
            String groupBy = e.groupByProperty();
            if (groupBy == null) {
                return exclusive;
            }

            String group;
            if ("branch".equals(groupBy)) {
                group = payload.getBranch();
            } else if (groupBy.startsWith("event")) {
                String[] payloadPath = groupBy.split("\\.");
                if (payloadPath.length == 1) {
                    throw new IllegalArgumentException("Invalid groupBy: '" + groupBy + "'");
                }

                payloadPath = Arrays.copyOfRange(payloadPath, 1, payloadPath.length);
                Object maybeString = ConfigurationUtils.get(payload.raw(), payloadPath);
                if (maybeString == null || (maybeString instanceof String)) {
                    group = (String) maybeString;
                } else {
                    String value = maybeString + " (class: " + maybeString.getClass() + ")";
                    throw new IllegalArgumentException("Expected string value for groupBy: '" + groupBy + "', got " + value);
                }
            } else {
                throw new IllegalArgumentException("Unknown groupBy: '" + groupBy + "'");
            }

            if (group == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("group", group);
            result.put("mode", e.mode().name());
            return result;
        }
    }

    private class GithubEventInitiatorSupplier implements Supplier<UserEntry> {

        private final UserManager userManager;
        private final LdapManager ldapManager;
        private final Payload payload;
        private final Supplier<UserEntry> fallback;

        public GithubEventInitiatorSupplier(UserManager userManager, LdapManager ldapManager, Payload payload) {
            this.userManager = userManager;
            this.ldapManager = ldapManager;
            this.payload = payload;
            this.fallback = () -> {
                String initiator = payload.getSender();
                if (initiator == null || initiator.trim().isEmpty()) {
                    throw new ConcordApplicationException("Can't determine initiator: " + payload);
                }

                return userManager.getOrCreate(initiator, null, UserType.LDAP)
                        .orElseThrow(() -> new ConcordApplicationException("User not found: " + initiator));
            };
        }

        @Override
        public UserEntry get() {
            if (!githubCfg.isUseSenderLdapDn() && !githubCfg.isUseSenderEmail()) {
                // don't try to match against payload sender's ldap_dn or email
                return fallback.get();
            }

            // only LDAP users are supported in GitHub triggers
            // ideally, match against exact LDAP DN (requires GitHub to be integrated with LDAP)
            if (githubCfg.isUseSenderLdapDn()) {
                UserEntry fromDn = findSenderDnInLdap();
                if (fromDn != null) {
                    return fromDn;
                }
            }

            // alternatively, user email may work (e.g. from SSO provider which has upstream LDAP source)
            if (githubCfg.isUseSenderEmail()) {
                UserEntry fromEmail = findSenderEmailInLdap();
                if (fromEmail != null) {
                    return fromEmail;
                }
            }

            log.warn("getOrCreateUserEntry ['{}'] -> can't determine the sender's 'ldap_dn' or 'email', falling back to 'login'", payload);
            return fallback.get();
        }

        private UserEntry findSenderDnInLdap() {
            String ldapDn = payload.getSenderLdapDn();
            if (ldapDn == null || ldapDn.isBlank()) {
                return null;
            }

            try {
                LdapPrincipal p = ldapManager.getPrincipalByDn(ldapDn);

                if (p == null) {
                    log.warn("getOrCreateUserEntry ['{}'] -> can't find user by ldap DN ({})", payload, ldapDn);
                    return null;
                }

                return userManager.getOrCreate(p.getUsername(), p.getDomain(), UserType.LDAP)
                        .orElseThrow(() -> new ConcordApplicationException("User not found: " + p.getUsername()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private UserEntry findSenderEmailInLdap() {
            String email = getEmail();
            if (email == null || email.isBlank()) {
                return null;
            }

            try {
                LdapPrincipal p = ldapManager.getPrincipalByMail(email);

                if (p == null) {
                    log.warn("getOrCreateUserEntry ['{}'] -> can't find user by ldap mail ({})", payload, email);
                    return null;
                }

                return userManager.getOrCreate(p.getUsername(), p.getDomain(), UserType.LDAP)
                        .orElseThrow(() -> new ConcordApplicationException("User not found: " + p.getUsername()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String getEmail() {
            URI repoUrl = GithubUtils.getRepoCloneUrl(payload);
            URI userUrl = GithubUtils.getSenderUrl(payload);

            try {
                return ghUserEmailCache.get(new EmailCacheKey(repoUrl, userUrl))
                        .orElse(null);
            } catch (ExecutionException ee) {
                Throwable t = ee.getCause();
                log.warn(ERROR_USER_EMAIL_LOOKUP, userUrl, t.getMessage());
            }

            return null;
        }
    }

    private static class UserLookupException extends Exception {
        public UserLookupException(String message) {
            super(message);
        }
    }

    private record GitHubUser(String email) {
    }

    private record EmailCacheKey(@Nonnull URI repoUrl, @Nonnull URI userUrl) {

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            EmailCacheKey that = (EmailCacheKey) o;
            // userUrl is sufficient for equality for caching--it will point to
            // the same user regardless of repoUrl.
            // repoUrl is only necessary to acquire token, not for caching.
            return userUrl().equals(that.userUrl());
        }

        @Override
        public int hashCode() {
            return userUrl().hashCode();
        }
    }

    private static class EmailCacheLoader extends CacheLoader<EmailCacheKey, Optional<String>> {

        private final Map<String, Long> rateLimitGauges;
        private final AuthTokenProvider authTokenProvider;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        public EmailCacheLoader(GithubConfiguration githubCfg,
                                Map<String, Long> rateLimitGauges,
                                AuthTokenProvider authTokenProvider,
                                ObjectMapper objectMapper) {

            this.rateLimitGauges = rateLimitGauges;
            this.authTokenProvider = authTokenProvider;
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(githubCfg.getHttpClientTimeout())
                    .build();
        }

        @Override
        public @Nonnull Optional<String> load(@Nonnull EmailCacheKey key) throws Exception {
            URI repoUrl = key.repoUrl();
            URI userUrl = key.userUrl();

            Optional<ExternalAuthToken> t = authTokenProvider.getToken(repoUrl, null);

            if (t.isEmpty()) {
                return Optional.empty();
            }

            HttpRequest req = HttpRequest.newBuilder(userUrl)
                    .GET()
                    .header("Authorization", "Bearer " + t.get().token())
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, BodyHandlers.ofInputStream());

            rateLimitGauges.put(t.get().authId(), resp.headers()
                    .firstValueAsLong("X-RateLimit-Remaining")
                    .orElse(-1L));

            if (resp.statusCode() != 200) {
                throw new UserLookupException("Non-200 response [: " + resp.statusCode() + "]: " + readBody(resp));
            }

            GitHubUser m = objectMapper.readValue(resp.body(), GitHubUser.class);

            return Optional.ofNullable(m.email());
        }

        private static String readBody(HttpResponse<InputStream> resp) {
            try (InputStream is = resp.body()) {
                return new String(is.readAllBytes());
            } catch (IOException e) {
                return "error reading response body: " + e.getMessage();
            }
        }
    }
}
