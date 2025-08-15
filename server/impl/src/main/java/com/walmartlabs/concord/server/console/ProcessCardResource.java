package com.walmartlabs.concord.server.console;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Path("/api/v1/")
@Tag(name = "ProcessCards")
public class ProcessCardResource implements Resource {

    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private final ProcessCardManager processCardManager;
    private final OrganizationManager organizationManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ConcordObjectMapper objectMapper;

    @Inject
    public ProcessCardResource(OrganizationManager organizationManager,
                               ProcessCardManager processCardManager,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao, ConcordObjectMapper objectMapper) {
        this.organizationManager = organizationManager;
        this.processCardManager = processCardManager;

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/processcard")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List user process cards", operationId = "listUserProcessCards")
    public List<ProcessCardEntry> list() {

        UserPrincipal user = UserPrincipal.assertCurrent();

        return processCardManager.listUserCards(user.getId());
    }

    @GET
    @Path("/processcard/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get process card", operationId = "getProcessCard")
    public ProcessCardEntry get(@PathParam("id") UUID cardId) throws IOException {
        return assertCard(cardId);
    }

    @DELETE
    @Path("/processcard/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete process card", operationId = "deleteProcessCard")
    public GenericOperationResult delete(@PathParam("id") UUID cardId) throws IOException {

        assertCard(cardId);
        processCardManager.assertAccess(cardId);

        processCardManager.delete(cardId);

        return new GenericOperationResult(OperationResult.DELETED);
    }

    @POST
    @Path("/processcard/{id}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Process cards access", operationId = "processCardAccess")
    public GenericOperationResult access(
            @PathParam("id") UUID cardId,
            ProcessCardAccessEntry entry) throws IOException {

        assertCard(cardId);
        processCardManager.assertAccess(cardId);

        processCardManager.updateAccess(cardId, entry.teamIds(), entry.userIds());

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @POST
    @Path("/processcard")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Create or update process card", operationId = "createOrUpdateProcessCard")
    public ProcessCardOperationResponse createOrUpdate(
            @Parameter(schema = @Schema(type = "object", implementation = ProcessCardRequest.class)) MultipartInput input) throws IOException {

        try {
            ProcessCardRequest r = ProcessCardRequest.from(input);

            UUID orgId = organizationManager.assertAccess(r.getOrgId(), r.getOrgName(), false).getId();
            UUID projectId = assertProject(orgId, r);
            UUID repoId = getRepo(projectId, r);
            String name = r.getName();
            Optional<String> entryPoint = Optional.ofNullable(r.getEntryPoint());
            String description = r.getDescription();
            Map<String, Object> data = r.getData();
            UUID id = r.getId();
            Integer orderId = r.getOrderId();

            try (InputStream icon = r.getIcon();
                 InputStream form = r.getForm()) {
                return processCardManager.createOrUpdate(id, projectId, repoId, name, entryPoint, description, icon, form, data, orderId);
            }
        } finally {
            input.close();
        }
    }

    @GET
    @Path("/processcard/{cardId}/form")
    @Produces(MediaType.TEXT_HTML)
    @WithTimer
    @Operation(description = "Get process card form", operationId = "getProcessCardForm")
    @ApiResponse(responseCode = "200", description = "Process form content",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string", format = "binary")))
    public Response getForm(@PathParam("cardId") UUID cardId) {

        assertCard(cardId);

        Optional<java.nio.file.Path> o = processCardManager.getForm(cardId, src -> {
            try {
                java.nio.file.Path tmp = PathUtils.createTempFile("process-form", ".html");
                Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(tmp);
            } catch (IOException e) {
                throw new ConcordApplicationException("Error while downloading custom process start form: " + cardId, e);
            }
        });

        if (o.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return toBinaryResponse(o.get());
    }

    @GET
    @Path("/processcard/{cardId}/data.js")
    @Produces("text/javascript")
    @WithTimer
    @Operation(description = "Get process card form data", operationId = "getProcessCardFormData")
    @ApiResponse(responseCode = "200", description = "Process form data content",
            content = @Content(mediaType = "text/javascript", schema = @Schema(type = "string", format = "binary")))
    public Response getFormData(@PathParam("cardId") UUID cardId) {
        ProcessCardEntry card = assertCard(cardId);

        Map<String, Object> customData = processCardManager.getFormData(cardId);

        Map<String, Object> resultData = new HashMap<>(customData != null ? customData : Collections.emptyMap());
        resultData.put("org", card.orgName());
        resultData.put("project", card.projectName());
        resultData.put("repo", card.repoName());
        resultData.put("entryPoint", card.entryPoint());

        return Response.ok(formatData(resultData))
                .build();
    }

    private ProcessCardEntry assertCard(UUID id) {
        ProcessCardEntry e = processCardManager.get(id);
        if (e == null) {
            throw new ConcordApplicationException("Process card not found: " + id, Response.Status.NOT_FOUND);
        }
        return e;
    }

    private UUID assertProject(UUID orgId, ProcessCardRequest request) {
        UUID id = request.getProjectId();
        String name = request.getProjectName();
        if (id == null && name != null) {
            if (orgId == null) {
                throw new ValidationErrorsException("Organization ID or name is required");
            }

            id = projectDao.getId(orgId, name);
            if (id == null) {
                throw new ValidationErrorsException("Project not found: " + name);
            }
        }
        return id;
    }

    private UUID getRepo(UUID projectId, ProcessCardRequest request) {
        UUID id = request.getRepoId();
        String name = request.getRepoName();
        if (id == null && name != null) {
            if (projectId == null) {
                throw new ValidationErrorsException("Project ID or name is required");
            }

            id = repositoryDao.getId(projectId, name);
            if (id == null) {
                throw new ValidationErrorsException("Repository not found: " + name);
            }
        }
        return id;
    }

    private String formatData(Map<String, Object> data) {
        return String.format(DATA_FILE_TEMPLATE, objectMapper.toString(data));
    }

    private static Response toBinaryResponse(java.nio.file.Path file) {
        return Response.ok((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(file)) {
                IOUtils.copy(in, out);
            } finally {
                Files.delete(file);
            }
        }).build();
    }

}
