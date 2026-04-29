package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedKeyPair;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/org")
@Tag(name = "Secrets")
public class SecretResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(SecretResource.class);

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final SecretManager secretManager;
    private final SecretDao secretDao;
    private final TeamDao teamDao;
    private final ProjectDao projectDao;
    private final UserManager userManager;

    @Inject
    public SecretResource(OrganizationManager orgManager,
                          OrganizationDao orgDao,
                          SecretManager secretManager,
                          SecretDao secretDao,
                          TeamDao teamDao,
                          ProjectDao projectDao,
                          UserManager userManager) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.secretManager = secretManager;
        this.secretDao = secretDao;
        this.teamDao = teamDao;
        this.projectDao = projectDao;
        this.userManager = userManager;
    }

    @POST
    @Path("/{orgName}/secret")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create secret", operationId = "createSecret")
    public SecretOperationResponse create(@PathParam("orgName") @ConcordKey String orgName,
                                          @Parameter(schema = @Schema(type = "object", implementation = Object.class)) MultipartInput input) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        try {
            SecretType type = SecretResourceUtils.assertType(input);
            String storeType = SecretResourceUtils.assertStoreType(secretManager, input);

            String name = SecretResourceUtils.assertName(input);
            SecretResourceUtils.assertUnique(secretDao, org.getId(), name);

            boolean generatePwd = MultipartUtils.getBoolean(input, Constants.Multipart.GENERATE_PASSWORD, false);
            String storePwd = SecretResourceUtils.getOrGenerateStorePassword(input, generatePwd);
            SecretVisibility visibility = SecretResourceUtils.getVisibility(input);

            Set<UUID> projectIds = getProjectIds(
                    org.getId(),
                    MultipartUtils.getUUIDList(input, Constants.Multipart.PROJECT_IDS),
                    MultipartUtils.getStringList(input, Constants.Multipart.PROJECT_NAMES),
                    MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID),
                    MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME)
            );
            switch (type) {
                case KEY_PAIR: {
                    return SecretResourceUtils.createKeyPair(secretManager, org.getId(), projectIds, name, storePwd, visibility, input, storeType);
                }
                case USERNAME_PASSWORD: {
                    return SecretResourceUtils.createUsernamePassword(secretManager, org.getId(), projectIds, name, storePwd, visibility, input, storeType);
                }
                case DATA: {
                    return SecretResourceUtils.createData(secretManager, org.getId(), projectIds, name, storePwd, visibility, input, storeType);
                }
                default:
                    throw new ValidationErrorsException("Unsupported secret type: " + type);
            }
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while processing the request: " + e.getMessage(), e);
        } finally {
            input.close();
        }
    }


    @POST
    @Path("/{orgName}/secret/{secretName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Update secret", operationId = "updateSecretV1")
    public GenericOperationResult update(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("secretName") @ConcordKey String secretName,
                                         @Valid SecretUpdateRequest req) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        try {
            UUID projectId = getProject(req.orgId(), req.projectId(), req.projectName());
            SecretUpdateParams newSecretParams = SecretUpdateParams.builder()
                    .newOrgId(req.orgId())
                    .newOrgName(req.orgName())
                    .newProjectIds(projectId == null ? null : Collections.singletonList(projectId))
                    .removeProjectLink(req.projectName() != null && req.projectName().trim().isEmpty())
                    .newOwnerId(getOwnerId(req.owner()))
                    .currentPassword(req.storePassword())
                    .newPassword(req.newStorePassword())
                    .newSecret(req.data() != null ? secretManager.buildBinaryData(new ByteArrayInputStream(Objects.requireNonNull(req.data()))) : null)
                    .newName(req.name())
                    .newVisibility(req.visibility())
                    .build();

            secretManager.update(org.getId(), secretName, newSecretParams);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while processing the request: " + e.getMessage(), e);
        }

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @GET
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Deprecated
    public SecretEntryV2 get(@PathParam("orgName") @ConcordKey String orgName,
                             @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.assertAccess(org.getId(), null, secretName, ResourceAccessLevel.READER, false);
    }

    @POST
    @Path("/{orgName}/secret/{secretName}/data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Operation(description = "Get an existing secret's data", operationId = "getSecretData")
    @ApiResponse(description = "Secret content",
            headers = @Header(name = Constants.Headers.SECRET_TYPE, schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response getData(@PathParam("orgName") @ConcordKey String orgName,
                            @PathParam("secretName") @ConcordKey String secretName,
                            @Parameter(schema = @Schema(type = "object", implementation = GetDataRequest.class)) MultipartInput input) {

        try {
            GetDataRequest request = GetDataRequest.from(input);

            OrganizationEntry org = orgManager.assertAccess(orgName, false);
            String password = request.getPassword();

            SecretDao.SecretDataEntry entry;
            try {
                entry = secretManager.getRaw(SecretManager.AccessScope.apiRequest(), org.getId(), secretName, password);
                if (entry == null) {
                    throw new WebApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
                }
            } catch (SecurityException e) {
                log.warn("fetchSecret -> error: {}", e.getMessage());
                throw new SecretException("Error while fetching a secret '" + secretName + "': " + e.getMessage());
            } catch (ValidationErrorsException e) {
                log.warn("fetchSecret -> error: {}", e.getMessage());
                return null;
            }

            try {
                return Response.ok((StreamingOutput) output -> output.write(entry.getData()),
                                MediaType.APPLICATION_OCTET_STREAM)
                        .header(Constants.Headers.SECRET_TYPE, entry.getType().name())
                        .build();
            } catch (Exception e) {
                log.error("fetchSecret ['{}'] -> error while fetching a secret", secretName, e);
                throw new ConcordApplicationException("Error while fetching a secret '" + secretName + "': " + e.getMessage());
            }
        } finally {
            input.close();
        }
    }

    @GET
    @Path("/{orgName}/secret/{secretName}/public")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @WithTimer
    @Operation(description = "Retrieves the public key of a key pair")
    public PublicKeyResponse getPublicKey(@PathParam("orgName") @ConcordKey String orgName,
                                          @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        try {
            DecryptedKeyPair k = secretManager.getKeyPair(SecretManager.AccessScope.internal(), org.getId(), secretName);
            return new PublicKeyResponse(k.getId(), null, null, new String(k.getData()));
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("getPublicKey -> error: {}", e.getMessage());
            throw new SecretException("Error while fetching a secret '" + secretName + "': " + e.getMessage());
        }
    }

    @GET
    @Path("/{orgName}/secret")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Deprecated
    public List<SecretEntryV2> list(@PathParam("orgName") @ConcordKey String orgName,
                                    @QueryParam("offset") int offset,
                                    @QueryParam("limit") int limit,
                                    @QueryParam("filter") String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.list(org.getId(), offset, limit, filter);
    }

    @DELETE
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Delete an existing secret")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        secretManager.delete(org.getId(), secretName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @POST
    @Path("/{orgName}/secret/{secretName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified secret and team", operationId = "updateSecretAccessLevel")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("secretName") @ConcordKey String secretName,
                                                    @Valid ResourceAccessEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID secretId = secretDao.getId(org.getId(), secretName);
        if (secretId == null) {
            throw new ConcordApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
        }

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);

        secretManager.updateAccessLevel(secretId, teamId, entry.getLevel());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @GET
    @Path("/{orgName}/secret/{secretName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get secret team access", operationId = "getSecretAccessLevel")
    public List<ResourceAccessEntry> getAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.getAccessLevel(org.getId(), secretName);
    }

    @POST
    @Path("/{orgName}/secret/{secretName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified secret and team", operationId = "updateSecretAccessLevelBulk")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("secretName") @ConcordKey String secretName,
                                                    @Valid Collection<ResourceAccessEntry> entries) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID secretId = secretDao.getId(org.getId(), secretName);
        if (secretId == null) {
            throw new ConcordApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
        }

        if (entries == null) {
            throw new ConcordApplicationException("List of teams is null.", Status.BAD_REQUEST);
        }

        secretManager.updateAccessLevel(secretId, entries, true);

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private Set<UUID> getProjectIds(UUID orgId, List<UUID> projectIds, List<String> projectNames, UUID projectId, String projectName) {
        if (projectIds == null || projectIds.isEmpty()) {
            if (projectNames != null && !projectNames.isEmpty()) {
                projectIds = projectNames.stream().map(name -> getProjectIdFromName(orgId, name)).collect(Collectors.toList());
            } else {
                if (projectId != null) {
                    projectIds = Collections.singletonList(projectId);
                } else if (projectName != null) {
                    projectIds = Collections.singletonList(getProjectIdFromName(orgId, projectName));
                }
            }
        }
        return (projectIds == null) ? null : new HashSet<>(projectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    private UUID getProjectIdFromName(UUID orgId, String projectName) {
        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }
        return id;
    }

    private UUID getProject(UUID orgId, UUID id, String name) {
        if (id == null && (name != null && !name.trim().isEmpty())) {
            id = projectDao.getId(orgId, name);
            if (id == null) {
                throw new ValidationErrorsException("Project not found: " + name);
            }
        }
        return id;
    }

    private UUID getOwnerId(EntityOwner owner) {
        if (owner == null) {
            return null;
        }

        if (owner.id() != null) {
            return userManager.get(owner.id())
                    .orElseThrow(() -> new ValidationErrorsException("User not found: " + owner.id()))
                    .getId();
        }

        if (owner.username() != null) {
            // TODO don't assume LDAP here
            return userManager.get(owner.username(), owner.userDomain(), UserType.LDAP)
                    .orElseThrow(() -> new ConcordApplicationException("User not found: " + owner.username()))
                    .getId();
        }

        return null;
    }
}
