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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.events.github.GithubTriggerProcessor;
import com.walmartlabs.concord.server.events.github.Payload;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.walmartlabs.concord.common.MemoSupplier.memo;
import static com.walmartlabs.concord.server.events.github.Constants.COMMIT_ID_KEY;
import static com.walmartlabs.concord.server.events.github.Constants.EVENT_SOURCE;

/**
 * Handles external GitHub events.
 * Uses a custom authentication mechanism,
 * see {@link com.walmartlabs.concord.server.security.GithubAuthenticatingFilter}.
 * <p>
 * See also https://developer.github.com/webhooks/
 */
@Named
@Singleton
@Api(value = "GitHub Events", authorizations = {})
@Path("/events/github")
public class GithubEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResource.class);

    private final GithubConfiguration githubCfg;
    private final TriggerProcessExecutor executor;
    private final AuditLog auditLog;
    private final List<GithubTriggerProcessor> processors;
    private final UserManager userManager;
    private final LdapManager ldapManager;
    private final TriggerEventInitiatorResolver initiatorResolver;

    @Inject
    public GithubEventResource(GithubConfiguration githubCfg,
                               TriggerProcessExecutor executor, AuditLog auditLog,
                               List<GithubTriggerProcessor> processors,
                               UserManager userManager,
                               LdapManager ldapManager,
                               TriggerEventInitiatorResolver initiatorResolver) {

        this.githubCfg = githubCfg;
        this.executor = executor;
        this.auditLog = auditLog;
        this.processors = processors;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
        this.initiatorResolver = initiatorResolver;
    }

    @POST
    @ApiOperation("Handles GitHub repository level events")
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @WithTimer
    public String onEvent(@ApiParam Map<String, Object> data,
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
        processors.forEach(p -> p.process(eventName, payload, uriInfo, results));

        for (GithubTriggerProcessor.Result r : results) {
            Event e = Event.builder()
                    .id(deliveryId)
                    .name(EVENT_SOURCE)
                    .attributes(r.event())
                    .initiator(memo(new GithubEventInitiatorSupplier(userManager, ldapManager, r.event())))
                    .build();

            executor.execute(e, r.triggers(), initiatorResolver, (t, cfg) -> {
                // if `useEventCommitId` is true then the process is forced to use the specified commit ID
                String commitId = MapUtils.getString(r.event(), COMMIT_ID_KEY);
                if (commitId != null && TriggerUtils.isUseEventCommitId(t)) {
                    cfg.put(Constants.Request.REPO_COMMIT_ID, commitId);
                }
                return cfg;
            });
        }

        return "ok";
    }

    private class GithubEventInitiatorSupplier implements Supplier<UserEntry> {

        private final UserManager userManager;
        private final LdapManager ldapManager;
        private final Map<String, Object> eventAttributes;
        private final EventInitiatorSupplier fallback;

        public GithubEventInitiatorSupplier(UserManager userManager, LdapManager ldapManager, Map<String, Object> eventAttributes) {
            this.userManager = userManager;
            this.ldapManager = ldapManager;
            this.eventAttributes = eventAttributes;
            this.fallback = new EventInitiatorSupplier("author", userManager, eventAttributes);
        }

        @Override
        public UserEntry get() {
            if (!githubCfg.isUseSenderLdapDn()) {
                return fallback.get();
            }

            String ldapDn = getSenderLdapDn(eventAttributes);
            if (ldapDn == null) {
                log.warn("getOrCreateUserEntry ['{}'] -> can't determine the sender's 'ldap_dn', falling back to 'login'", eventAttributes);
                return fallback.get();
            }

            // only LDAP users are supported in GitHub triggers
            try {
                LdapPrincipal p = ldapManager.getPrincipalByDn(ldapDn);
                if (p == null) {
                    log.warn("getOrCreateUserEntry ['{}'] -> can't find user by ldap DN ({})", eventAttributes, ldapDn);
                    return fallback.get();
                }

                return userManager.getOrCreate(p.getUsername(), p.getDomain(), UserType.LDAP)
                        .orElseThrow(() -> new ConcordApplicationException("User not found: " + p.getUsername()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String getSenderLdapDn(Map<String, Object> event) {
            Object result = ConfigurationUtils.get(event, "payload.sender.ldap_dn");
            if (result instanceof String) {
                return (String) result;
            }
            return null;
        }
    }
}
