package com.walmartlabs.concord.server.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.OffsetDateTimeParam;
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreAccessManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreEntry;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.secret.SecretEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.Utils.unwrap;

@Path("/api/v1/audit")
@Tag(name = "Audit Log")
public class AuditLogResource implements Resource {

    private static final String DETAILS_QUERY_PARAMETER_PREFIX = "details.";

    // array of the allowed keys in "/api/v1/audit?details.[fieldName]=[value]"
    private static final String[] ALLOWED_DETAILS_KEYS = {
            "eventId",
            "githubEvent",
            "fullRepoName",
            "orgId",
            "orgName",
            "projectId",
            "projectName",
            "secretId",
            "secretName",
            "source",
            "jsonStoreId",
            "jsonStoreName",
            "teamId",
            "teamName"
    };

    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;
    private final SecretManager secretManager;
    private final JsonStoreAccessManager jsonStoreAccessManager;
    private final UserDao userDao;
    private final TeamDao teamDao;
    private final AuditDao auditDao;
    private final AuditConfiguration cfg;

    @Inject
    public AuditLogResource(OrganizationManager orgManager,
                            ProjectAccessManager projectAccessManager,
                            SecretManager secretManager,
                            JsonStoreAccessManager jsonStoreAccessManager,
                            UserDao userDao,
                            TeamDao teamDao,
                            AuditDao auditDao,
                            AuditConfiguration cfg) {

        this.orgManager = orgManager;
        this.projectAccessManager = projectAccessManager;
        this.secretManager = secretManager;
        this.jsonStoreAccessManager = jsonStoreAccessManager;
        this.userDao = userDao;
        this.teamDao = teamDao;
        this.auditDao = auditDao;
        this.cfg = cfg;
    }

    /**
     * Returns a list of audit log events for the specified filters.
     * <p>
     * The endpoint performs additional permission checks if an entity filter
     * (org, project, etc) is specified. If no filters specified the admin
     * privileges are required.
     * <p>
     * The endpoint ignores all "unknown" filters. Only the {@link #ALLOWED_DETAILS_KEYS}
     * are allowed.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List audit log entries for the specified organization")
    public List<AuditLogEntry> list(@QueryParam("object") AuditObject object,
                                    @QueryParam("action") AuditAction action,
                                    @QueryParam("userId") UUID userId,
                                    @QueryParam("username") String username,
                                    @QueryParam("after") OffsetDateTimeParam afterTimestamp,
                                    @QueryParam("before") OffsetDateTimeParam beforeTimestamp,
                                    @QueryParam("offset") @DefaultValue("0") int offset,
                                    @QueryParam("limit") @DefaultValue("30") int limit,
                                    @Context UriInfo uriInfo) {

        Map<String, String> details = getDetails(uriInfo);

        UUID effectiveUserId = userId;
        if (effectiveUserId == null && username != null) {
            effectiveUserId = userDao.getId(username, null, null);
            if (effectiveUserId == null) {
                // no such user in our DB, there shouldn't be any audit logs anyway
                return Collections.emptyList();
            }
        }

        UUID effectiveOrgId = getEffectiveOrgId(details);
        UUID effectiveProjectId = getEffectiveProjectId(effectiveOrgId, details);
        UUID effectiveSecretId = getEffectiveSecretId(effectiveOrgId, details);
        UUID effectiveJsonStoreId = getEffectiveJsonStoreId(effectiveOrgId, details);
        UUID effectiveTeamId = getEffectiveTeamId(effectiveOrgId, details);
        String source = details.get("source");
        Object eventId = details.get("eventId");

        // only admins are allowed to proceed without any entity filters
        if (effectiveOrgId == null
                && effectiveProjectId == null
                && effectiveSecretId == null
                && effectiveJsonStoreId == null
                && effectiveTeamId == null
                && source == null
                && eventId == null) {

            if (!Roles.isAdmin()) {
                throw new UnauthorizedException("Only admins can retrieve audit events without filtering by entity.");
            }
        }

        ImmutableAuditLogFilter.Builder filterBuilder = AuditLogFilter.builder();

        if (effectiveOrgId != null) {
            filterBuilder.putDetails("orgId", effectiveOrgId);
        }

        if (effectiveProjectId != null) {
            filterBuilder.putDetails("projectId", effectiveProjectId);
        }

        if (effectiveSecretId != null) {
            filterBuilder.putDetails("secretId", effectiveSecretId);
        }

        if (effectiveJsonStoreId != null) {
            filterBuilder.putDetails("jsonStoreId", effectiveJsonStoreId);
        }

        if (effectiveTeamId != null) {
            filterBuilder.putDetails("teamId", effectiveTeamId);
        }

        if (source != null) {
            filterBuilder.putDetails("source", source);
        }

        if (eventId != null) {
            filterBuilder.putDetails("eventId", eventId);
        }

        if (details.get("githubEvent") != null) {
            filterBuilder.putDetails("githubEvent", details.get("githubEvent"));
        }

        if (details.get("fullRepoName") != null) {
            filterBuilder.putDetails("payload",
                    Collections.singletonMap("repository",
                            Collections.singletonMap("full_name", details.get("fullRepoName"))));
        }

        assertTimeInterval(unwrap(afterTimestamp), unwrap(beforeTimestamp));

        return auditDao.list(filterBuilder
                .userId(effectiveUserId)
                .object(object)
                .action(action)
                .after(unwrap(afterTimestamp))
                .before(unwrap(beforeTimestamp))
                .limit(limit)
                .offset(offset)
                .build());
    }

    private UUID getEffectiveOrgId(Map<String, String> details) {
        UUID orgId = getUUID(details, "orgId");
        String orgName = details.get("orgName");

        if (orgId != null || orgName != null) {
            OrganizationEntry org = orgManager.assertAccess(orgId, orgName, true);
            return org.getId();
        }

        return null;
    }

    private UUID getEffectiveProjectId(UUID effectiveOrgId, Map<String, String> details) {
        UUID projectId = getUUID(details, "projectId");
        String projectName = details.get("projectName");

        if (effectiveOrgId == null && projectId == null && projectName != null) {
            throw new ValidationErrorsException("'orgId' or 'orgName' is required");
        }

        if (projectId != null || projectName != null) {
            ProjectEntry project = projectAccessManager.assertAccess(effectiveOrgId, projectId, projectName, ResourceAccessLevel.READER, true);
            return project.getId();
        }

        return null;
    }

    private UUID getEffectiveSecretId(UUID effectiveOrgId, Map<String, String> details) {
        UUID secretId = getUUID(details, "secretId");
        String secretName = details.get("secretName");

        if (effectiveOrgId == null && secretId == null && secretName != null) {
            throw new ValidationErrorsException("'orgId' or 'orgName' is required");
        }

        if (secretId != null || secretName != null) {
            SecretEntry secret = secretManager.assertAccess(effectiveOrgId, secretId, secretName, ResourceAccessLevel.READER, true);
            return secret.getId();
        }

        return null;
    }

    private UUID getEffectiveJsonStoreId(UUID effectiveOrgId, Map<String, String> details) {
        UUID jsonStoreId = getUUID(details, "jsonStoreId");
        String jsonStoreName = details.get("jsonStoreName");

        if (effectiveOrgId == null && jsonStoreId == null && jsonStoreName != null) {
            throw new ValidationErrorsException("'orgId' or 'orgName' is required");
        }

        if (jsonStoreId != null || jsonStoreName != null) {
            JsonStoreEntry store = jsonStoreAccessManager.assertAccess(effectiveOrgId, jsonStoreId, jsonStoreName, ResourceAccessLevel.READER, true);
            return store.id();
        }

        return null;
    }

    private UUID getEffectiveTeamId(UUID effectiveOrgId, Map<String, String> details) {
        UUID teamId = getUUID(details, "teamId");
        String teamName = details.get("teamName");

        if (effectiveOrgId == null && teamId == null && teamName != null) {
            throw new ValidationErrorsException("'orgId' or 'orgName' is required");
        }

        if (teamId != null || teamName != null) {
            UUID effectiveTeamId = teamDao.getId(effectiveOrgId, teamName);
            if (effectiveTeamId == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }

            return effectiveTeamId;
        }

        return null;
    }

    private static Map<String, String> getDetails(UriInfo uriInfo) {
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        return params.entrySet().stream()
                .filter(e -> {
                    for (String k : ALLOWED_DETAILS_KEYS) {
                        if (e.getKey().equals(DETAILS_QUERY_PARAMETER_PREFIX + k)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toMap(e -> e.getKey().substring(DETAILS_QUERY_PARAMETER_PREFIX.length()), e -> e.getValue().get(0)));
    }

    private static UUID getUUID(Map<String, String> m, String k) {
        String s = m.get(k);
        if (s == null) {
            return null;
        }

        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Invalid request parameters. Expected a UUID in '" + k + "'");
        }
    }

    private void assertTimeInterval(OffsetDateTime after, OffsetDateTime before) {
        if (cfg.getMaxSearchInterval() == null) {
            return;
        }

        if (before == null && after == null) {
            return;
        }

        if (after != null && before != null) {
            if (Duration.between(after, before).compareTo(cfg.getMaxSearchInterval()) > 0) {
                throw new ConcordApplicationException("Max search interval exceeded. Current: " + Duration.between(after, before) + ", max: " + cfg.getMaxSearchInterval(), Response.Status.BAD_REQUEST);
            }
        } else if (after != null) {
            if (Duration.between(after, OffsetDateTime.now()).compareTo(cfg.getMaxSearchInterval()) > 0) {
                throw new ConcordApplicationException("Max search interval exceeded", Response.Status.BAD_REQUEST);
            }
        } else {
            throw new ConcordApplicationException("Specify after parameter", Response.Status.BAD_REQUEST);
        }
    }
}
