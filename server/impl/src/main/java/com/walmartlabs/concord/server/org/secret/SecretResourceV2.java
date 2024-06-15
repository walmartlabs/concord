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
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v2/org")
@Tag(name = "SecretsV2")
public class SecretResourceV2 implements Resource {

    private final OrganizationManager orgManager;
    private final SecretManager secretManager;
    private final ProjectDao projectDao;

    @Inject
    public SecretResourceV2(OrganizationManager orgManager,
                            SecretManager secretManager,
                            ProjectDao projectDao,
                            SecretDao secretDao) {

        this.orgManager = orgManager;
        this.secretManager = secretManager;
        this.projectDao = projectDao;
    }

    @GET
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get an existing secret", operationId = "getSecret")
    public SecretEntryV2 get(@PathParam("orgName") @ConcordKey String orgName,
                             @PathParam("secretName") @ConcordKey String secretName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.assertAccess(org.getId(), null, secretName, ResourceAccessLevel.READER, false);
    }


    @GET
    @Path("/{orgName}/secret")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "List secrets", operationId = "listSecrets")
    public List<SecretEntryV2> list(@PathParam("orgName") @ConcordKey String orgName,
                                    @QueryParam("offset") int offset,
                                    @QueryParam("limit") int limit,
                                    @QueryParam("filter") String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.list(org.getId(), offset, limit, filter);
    }

    @POST
    @Path("/{orgName}/secret/{secretName}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates an existing secret", operationId = "updateSecret")
    public GenericOperationResult update(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("secretName") @ConcordKey String secretName,
                                         @Parameter(schema = @Schema(type = "object", implementation = Object.class)) MultipartInput input) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        Set<UUID> projectIds = getProjectIds(
                org.getId(),
                MultipartUtils.getUUIDList(input, Constants.Multipart.PROJECT_IDS),
                MultipartUtils.getStringList(input, Constants.Multipart.PROJECT_NAMES),
                MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID),
                MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME)
        );
        try {
            SecretUpdateParams newSecretParams = SecretUpdateParams.builder()
                    .newOrgId(MultipartUtils.getUuid(input, Constants.Multipart.ORG_ID))
                    .newOrgName(MultipartUtils.getString(input, Constants.Multipart.ORG_NAME))
                    .newProjectIds(projectIds)
                    .removeProjectLink(MultipartUtils.getBoolean(input, "removeProjectLink", false))
                    .newOwnerId(MultipartUtils.getUuid(input, "ownerId"))
                    .currentPassword(MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD))
                    .newPassword(MultipartUtils.getString(input, Constants.Multipart.NEW_STORE_PASSWORD))
                    .newSecret(buildSecret(input))
                    .newName(MultipartUtils.getString(input, Constants.Multipart.NAME))
                    .newVisibility(SecretResourceUtils.getVisibility(input))
                    .build();

            secretManager.update(org.getId(), secretName, newSecretParams);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while processing the request: " + e.getMessage(), e);
        }

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    public Secret buildSecret(MultipartInput input) throws IOException {
        SecretType type = SecretResourceUtils.getType(input);
        if (type == null) {
            return null;
        }

        switch (type) {
            case KEY_PAIR: {
                InputStream publicKey = MultipartUtils.assertStream(input, Constants.Multipart.PUBLIC);
                InputStream privateKey = MultipartUtils.assertStream(input, Constants.Multipart.PRIVATE);
                return secretManager.buildKeyPair(publicKey, privateKey);
            }
            case USERNAME_PASSWORD: {
                String username = MultipartUtils.assertString(input, Constants.Multipart.USERNAME);
                String password = MultipartUtils.assertString(input, Constants.Multipart.PASSWORD);
                return secretManager.buildUsernamePassword(username, password.toCharArray());
            }
            case DATA: {
                InputStream data = MultipartUtils.assertStream(input, Constants.Multipart.DATA);
                return secretManager.buildBinaryData(data);
            }
            default:
                throw new ValidationErrorsException("Unsupported secret type: " + type);
        }
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
}
