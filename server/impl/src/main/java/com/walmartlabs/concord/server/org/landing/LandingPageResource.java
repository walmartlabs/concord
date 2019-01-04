package com.walmartlabs.concord.server.org.landing;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.*;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.jooq.Configuration;
import org.postgresql.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.org.project.RepositoryUtils.assertRepository;

@Named
@Singleton
@Api(value = "Landing Pages", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@javax.ws.rs.Path("/api/v1/org")
public class LandingPageResource extends AbstractDao implements Resource {

    private static final Logger log = LoggerFactory.getLogger(LandingPageResource.class);

    private static final String LP_META_FILE_NAME = "landing.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LandingDao landingDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectAccessManager projectAccessManager;
    private final RepositoryManager repositoryManager;
    private final OrganizationManager orgManager;

    @Inject
    public LandingPageResource(@Named("app") Configuration cfg,
                               LandingDao landingDao,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               ProjectAccessManager projectAccessManager,
                               RepositoryManager repositoryManager,
                               OrganizationManager orgManager) {

        super(cfg);

        this.landingDao = landingDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.projectAccessManager = projectAccessManager;
        this.repositoryManager = repositoryManager;
        this.orgManager = orgManager;
    }

    /**
     * Create or update a project's landing page registration.
     *
     * @param entry landing's data
     * @return
     */
    @POST
    @javax.ws.rs.Path("/{orgName}/landing_page")
    @ApiOperation("Create or update a project's landing page registration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CreateLandingResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                @ApiParam @Valid LandingEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        ProjectEntry project = assertProject(org.getId(), entry.getProjectName(), ResourceAccessLevel.WRITER, true);
        RepositoryEntry repository = assertRepository(project, entry.getRepositoryName());

        byte[] icon = null;
        if (entry.getIcon() != null) {
            icon = Base64.decode(entry.getIcon());
        }

        if (entry.getId() != null) {
            landingDao.update(entry.getId(), project.getId(), repository.getId(), entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.UPDATED, entry.getId());
        } else {
            UUID landingId = landingDao.insert(project.getId(), repository.getId(), entry.getName(), entry.getDescription(), icon);
            return new CreateLandingResponse(OperationResult.CREATED, landingId);
        }
    }

    /**
     * Lists landing page registrations.
     *
     * @return
     */
    @GET
    @javax.ws.rs.Path("/{orgName}/landing_page")
    @ApiOperation(value = "List landing page registrations", responseContainer = "list", response = LandingEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<LandingEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();

        if (p.isAdmin()) {
            // admins can see any LP
            userId = null;
        }

        return landingDao.list(org.getId(), userId);
    }

    /**
     * Refresh all landing page definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh all landing page definitions for all projects")
    @javax.ws.rs.Path("/landing_page/refresh")
    public Response refreshAll() {
        assertAdmin();
        repositoryDao.list().parallelStream().forEach(r -> {
            try {
                refresh(r);
            } catch (Exception e) {
                log.warn("refreshAll -> {} refresh failed: {}", r.getId(), e.getMessage());
            }
        });
        return Response.ok().build();
    }

    /**
     * Refresh landing page definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh landing page definitions for the specified project and repository")
    @javax.ws.rs.Path("{orgName}/landing_page/refresh/{projectName}/{repositoryName}")
    public Response refresh(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        ProjectEntry p = assertProject(org.getId(), projectName, ResourceAccessLevel.WRITER, true);
        RepositoryEntry r = assertRepository(p, repositoryName);

        refresh(r);

        return Response.ok().build();
    }

    private LandingEntry loadEntry(Path file) {
        if (!Files.exists(file)) {
            return null;
        }

        try {
            return objectMapper.readValue(file.toFile(), LandingEntry.class);
        } catch (IOException e) {
            // ignore
            return null;
        }
    }

    private void refresh(RepositoryEntry r) {
        LandingEntry le = repositoryManager.withLock(r.getUrl(), () ->  {
            Path lpMetaFile = repositoryManager.fetch(r.getProjectId(), r).path()
                            .resolve(LP_META_FILE_NAME);
            return loadEntry(lpMetaFile);
        });

        tx(tx -> {
            landingDao.delete(tx, r.getProjectId(), r.getId());
            if (le != null) {
                byte[] icon = le.getIcon() != null ? Base64.decode(le.getIcon()) : null;
                landingDao.insert(tx, r.getProjectId(), r.getId(), le.getName(), le.getDescription(), icon);
            }
        });

        log.info("refresh ['{}'] -> done", r.getId());
    }

    private ProjectEntry assertProject(UUID orgId, String projectName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (projectName == null) {
            throw new ValidationErrorsException("A valid project name is required");
        }

        return projectAccessManager.assertProjectAccess(orgId, null, projectName, accessLevel, orgMembersOnly);
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Not authorized");
        }
    }
}
