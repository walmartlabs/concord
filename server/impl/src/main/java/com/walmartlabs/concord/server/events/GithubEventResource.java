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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.events.github.GithubTriggerProcessor;
import com.walmartlabs.concord.server.events.github.Payload;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggerUtils;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
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

import static com.walmartlabs.concord.server.events.github.Constants.COMMIT_ID_KEY;
import static com.walmartlabs.concord.server.events.github.Constants.EVENT_SOURCE;

@Named
@Singleton
@Api(value = "GitHub Events", authorizations = {})
@Path("/events/github")
public class GithubEventResource extends AbstractEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(GithubEventResource.class);

    private final GithubConfiguration githubCfg;
    private final LdapManager ldapManager;
    private final UserManager userManager;
    private final AuditLog auditLog;
    private final List<GithubTriggerProcessor> processors;

    @Inject
    public GithubEventResource(ExternalEventsConfiguration cfg,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               ProcessManager processManager,
                               TriggersConfiguration triggersConfiguration,
                               GithubConfiguration githubCfg,
                               LdapManager ldapManager,
                               UserManager userManager,
                               ProcessSecurityContext processSecurityContext,
                               AuditLog auditLog,
                               List<GithubTriggerProcessor> processors) {

        super(cfg, processManager, projectDao, repositoryDao, triggersConfiguration, userManager, processSecurityContext);

        this.githubCfg = githubCfg;
        this.ldapManager = ldapManager;
        this.userManager = userManager;
        this.auditLog = auditLog;
        this.processors = processors;
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

        if (isDisabled(eventName)) {
            log.warn("event ['{}', '{}'] -> disabled", deliveryId, eventName);
            return "ok";
        }

        Payload payload = Payload.from(eventName, data);
        if (payload == null) {
            log.warn("event ['{}', '{}'] -> can't parse payload: '{}'", deliveryId, eventName, data);
            return "ok";
        }

        if (githubCfg.isLogEvents()) {
            auditLog.add(AuditObject.EXTERNAL_EVENT, AuditAction.ACCESS)
                    .field("source", EVENT_SOURCE)
                    .field("eventId", deliveryId)
                    .field("githubEvent", eventName)
                    .field("payload", payload)
                    .log();
        }

        List<GithubTriggerProcessor.Result> results = new ArrayList<>();
        processors.forEach(p -> p.process(eventName, payload, uriInfo, results));

        for (GithubTriggerProcessor.Result r : results) {
            process(deliveryId, EVENT_SOURCE, r.event(), r.triggers(), (t, cfg) -> {
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

    @Override
    protected UserEntry getOrCreateUserEntry(Map<String, Object> event) {
        if (!githubCfg.isUseSenderLdapDn()) {
            return super.getOrCreateUserEntry(event);
        }

        String ldapDn = getSenderLdapDn(event);
        if (ldapDn == null) {
            log.warn("getOrCreateUserEntry ['{}'] -> can't determine the sender's 'ldap_dn', falling back to 'login'", event);
            return super.getOrCreateUserEntry(event);
        }

        // only LDAP users are supported in GitHub triggers
        try {
            LdapPrincipal p = ldapManager.getPrincipalByDn(ldapDn);
            if (p == null) {
                log.warn("getOrCreateUserEntry ['{}'] -> can't find user by ldap DN ({})", event, ldapDn);
                return super.getOrCreateUserEntry(event);
            }

            return userManager.getOrCreate(p.getUsername(), p.getDomain(), UserType.LDAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String getSenderLdapDn(Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        if (payload == null) {
            return null;
        }

        Map<String, Object> sender = (Map<String, Object>) payload.get("sender");
        if (sender == null) {
            return null;
        }

        return (String) sender.get("ldap_dn");
    }
}
