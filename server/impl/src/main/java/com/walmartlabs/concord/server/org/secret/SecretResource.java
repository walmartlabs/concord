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
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Named
@Singleton
@OpenAPIDefinition()
@Tag(name = "Secrets")
@Path("/api/v1/org")
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
    @Operation(
            requestBody =
                @RequestBody(
                        content = @Content(
                                mediaType = MediaType.MULTIPART_FORM_DATA,
                                schema = @Schema(type = "object", implementation = CreateSecretRequest.class)
                        )
                )
    )
    public SecretOperationResponse create(@PathParam("orgName") @ConcordKey String orgName,
                                          @Parameter(schema = @Schema(name = "object")) MultipartInput input) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        try {
            SecretType type = SecretResourceUtils.assertType(input);
            String storeType = SecretResourceUtils.assertStoreType(secretManager, input);

            String name = SecretResourceUtils.assertName(input);
            SecretResourceUtils.assertUnique(secretDao, org.getId(), name);

            boolean generatePwd = MultipartUtils.getBoolean(input, Constants.Multipart.GENERATE_PASSWORD, false);
            String storePwd = SecretResourceUtils.getOrGenerateStorePassword(input, generatePwd);
            SecretVisibility visibility = SecretResourceUtils.getVisibility(input);

            Set<UUID> projectIds =  getProjectIds(
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
        }
    }

    public static class CreateSecretRequest {

        private final SecretType type;

        private final String name;

        @Schema(type = "string", format = "binary", description = "privateKey")
        private final InputStream privateKey;

        public CreateSecretRequest(SecretType type, String name, InputStream privateKey) {
            this.type = type;
            this.name = name;
            this.privateKey = privateKey;
        }

        public SecretType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public InputStream getPrivateKey() {
            return privateKey;
        }
    }

    @POST
//    @ApiOperation("Updates an existing secret") // weird URLs as a workaround for swagger-maven-plugin issue
    @Path("/{orgName}/secret/{secretName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Deprecated
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
//    @ApiOperation("Get an existing secret")
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public SecretEntry get(@PathParam("orgName") @ConcordKey String orgName,
                           @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.assertAccess(org.getId(), null, secretName, ResourceAccessLevel.READER, false);
    }

    @POST
//    @ApiOperation(value = "Get an existing secret's data", response = File.class, hidden = true)
//    @ApiResponses(value = {
//            @ApiResponse(code = 200, message = "OK",
//                    response = File.class,
//                    responseHeaders = @ResponseHeader(name = "X-Concord-SecretType", description = "Secret type", response = String.class))})
    @Path("/{orgName}/secret/{secretName}/data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    public Response getData(@PathParam("orgName") @ConcordKey String orgName,
                            @PathParam("secretName") @ConcordKey String secretName,
                            MultipartInput input) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        String password = MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD);

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
    }

    @GET
//    @ApiOperation("Retrieves the public key of a key pair")
    @Path("/{orgName}/secret/{secretName}/public")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @WithTimer
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
//    @ApiOperation(value = "List secrets", responseContainer = "list", response = SecretEntry.class)
    @Path("/{orgName}/secret")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public List<SecretEntry> list(@PathParam("orgName") @ConcordKey String orgName,
                                  @QueryParam("offset") int offset,
                                  @QueryParam("limit") int limit,
                                  @QueryParam("filter") String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.list(org.getId(), offset, limit, filter).stream().map(e -> (SecretEntry) e).collect(Collectors.toList());
    }

    @DELETE
//    @ApiOperation("Delete an existing secret")
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        secretManager.delete(org.getId(), secretName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @POST
//    @ApiOperation("Updates the access level for the specified secret and team")
    @Path("/{orgName}/secret/{secretName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
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
//    @ApiOperation("Get secret team access")
    @Path("/{orgName}/secret/{secretName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ResourceAccessEntry> getAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.getAccessLevel(org.getId(), secretName);
    }

    @POST
//    @ApiOperation("Updates the access level for the specified secret and team")
    @Path("/{orgName}/secret/{secretName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
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
        if (id == null && ( name != null && !name.trim().isEmpty()) ) {
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
