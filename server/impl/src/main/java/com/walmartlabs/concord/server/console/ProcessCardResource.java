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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.PayloadBuilder;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.Context;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Path("/api/v1/")
@Tag(name = "ProcessCards")
public class ProcessCardResource implements Resource {

    private static final String DATA_FILE_TEMPLATE = "data = %s;";
    static final String FORM_CSP = "sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline'; "
            + "style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'; "
            + "base-uri 'none'; object-src 'none'; frame-src 'none'; frame-ancestors 'self'";

    private final ProcessCardManager processCardManager;
    private final OrganizationManager organizationManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ConcordObjectMapper objectMapper;
    private final ProjectAccessManager projectAccessManager;
    private final ProcessManager processManager;

    @Inject
    public ProcessCardResource(OrganizationManager organizationManager,
                               ProcessCardManager processCardManager,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao, ConcordObjectMapper objectMapper,
                               ProjectAccessManager projectAccessManager, ProcessManager processManager) {
        this.organizationManager = organizationManager;
        this.processCardManager = processCardManager;

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.objectMapper = objectMapper;
        this.projectAccessManager = projectAccessManager;
        this.processManager = processManager;
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

        assertExistingCard(cardId);
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

        assertExistingCard(cardId);
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

            OrganizationEntry org = organizationManager.assertAccess(r.getOrgId(), r.getOrgName(), false);
            if (r.getOrgName() != null && !r.getOrgName().equals(org.getName())) {
                throw new ValidationErrorsException("Organization ID and name do not match");
            }
            UUID orgId = org.getId();
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
    @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
    @WithTimer
    @Operation(description = "Get process card form", operationId = "getProcessCardForm")
    @ApiResponse(responseCode = "200", description = "Sandboxed HTML for same-origin iframes, inert source otherwise",
            content = {
                    @Content(mediaType = "text/html", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "text/plain", schema = @Schema(type = "string", format = "binary"))
            })
    public Response getForm(@PathParam("cardId") UUID cardId, @Context HttpServletRequest request) {

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

        boolean embedded = request != null && "iframe".equals(request.getHeader("Sec-Fetch-Dest"))
                && "same-origin".equals(request.getHeader("Sec-Fetch-Site"));
        return toBinaryResponse(o.get(), embedded);
    }

    @GET
    @Path("/processcard/{cardId}/data.js")
    @Produces("text/javascript")
    @WithTimer
    @Operation(description = "Get process card form data", operationId = "getProcessCardFormData")
    @ApiResponse(responseCode = "200", description = "Process form data content",
            content = @Content(mediaType = "text/javascript", schema = @Schema(type = "string", format = "binary")))
    public Response getFormData(@PathParam("cardId") UUID cardId) {
        return Response.ok(formatData(readData(cardId)))
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    @GET
    @Path("/processcard/{cardId}/data")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get process card initial data", operationId = "getProcessCardData")
    @ApiResponse(responseCode = "200", description = "Initial card data",
            content = @Content(schema = @Schema(type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)))
    public Response getData(@PathParam("cardId") UUID cardId) {
        return Response.ok(readData(cardId)).header("Cache-Control", "no-store").build();
    }

    private Map<String, Object> readData(UUID cardId) {
        ProcessCardEntry card = assertCard(cardId);

        Map<String, Object> customData = processCardManager.getFormData(cardId);

        Map<String, Object> resultData = new HashMap<>(customData != null ? customData : Collections.emptyMap());
        resultData.put("org", card.orgName());
        resultData.put("project", card.projectName());
        resultData.put("repo", card.repoName());
        resultData.put("entryPoint", card.entryPoint());

        return resultData;
    }

    @POST
    @Path("/processcard/{cardId}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Start the process card target using JSON arguments only", operationId = "startProcessCard")
    public Map<String, UUID> start(@PathParam("cardId") UUID cardId, Map<String, Object> arguments,
                                   @Context HttpServletRequest request) throws IOException {
        assertCard(cardId);
        validateArguments(arguments);
        ProcessCardManager.Target target = processCardManager.getTarget(cardId);
        if (target == null) {
            throw new ConcordApplicationException("Process card not found: " + cardId, Response.Status.NOT_FOUND);
        }
        if (target.repositoryId() != null && !target.projectId().equals(repositoryDao.getProjectId(target.repositoryId()))) {
            throw new ValidationErrorsException("Repository must belong to the project");
        }
        ProjectEntry project = projectAccessManager.assertAccess(target.projectId(), ResourceAccessLevel.READER, true);
        // Check before entering the pipeline so authorization errors retain their HTTP status.
        switch (project.getProcessExecMode()) {
            case DISABLED -> throw new ConcordApplicationException("Process execution is disabled for the project.", Response.Status.FORBIDDEN);
            case WRITERS -> projectAccessManager.assertAccess(target.projectId(), ResourceAccessLevel.WRITER, true);
            case READERS -> {}
        }
        UserPrincipal user = UserPrincipal.assertCurrent();
        return Map.of("instanceId", processManager.start(PayloadBuilder.start(PartialProcessKey.create())
                .organization(projectDao.getOrgId(target.projectId()))
                .project(target.projectId())
                .repository(target.repositoryId())
                .entryPoint(target.entryPoint())
                .configuration(Map.of(Constants.Request.ARGUMENTS_KEY, arguments))
                .initiator(user.getId(), user.getUsername())
                .request(request)
                .build()).getInstanceId());
    }

    void validateArguments(Map<String, Object> arguments) {
        if (arguments == null || !isArgumentValue(arguments, 0) || objectMapper.toString(arguments).length() > 65536) {
            throw new ValidationErrorsException("Expected a JSON arguments object of at most 65536 characters");
        }
    }

    private static boolean isArgumentValue(Object value, int depth) {
        if (depth > 16) {
            return false;
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.stream().allMatch(v -> isArgumentValue(v, depth + 1));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().allMatch(e -> e.getKey() instanceof String
                    && !Set.of("__proto__", "constructor", "prototype").contains(e.getKey())
                    && isArgumentValue(e.getValue(), depth + 1));
        }
        return false;
    }

    private ProcessCardEntry assertCard(UUID id) {
        ProcessCardEntry e = assertExistingCard(id);
        processCardManager.assertReadable(id);
        return e;
    }

    private ProcessCardEntry assertExistingCard(UUID id) {
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
        if (id == null) {
            throw new ValidationErrorsException("Project ID or name is required");
        }
        ProjectEntry project = projectAccessManager.assertAccess(id, ResourceAccessLevel.WRITER, true);
        if (!orgId.equals(project.getOrgId()) || (name != null && !name.equals(project.getName()))) {
            throw new ValidationErrorsException("Project does not match the specified organization and name");
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
        if (id != null && (!projectId.equals(repositoryDao.getProjectId(id))
                || (name != null && !id.equals(repositoryDao.getId(projectId, name))))) {
            throw new ValidationErrorsException("Repository does not match the specified project and name");
        }
        return id;
    }

    private String formatData(Map<String, Object> data) {
        return String.format(DATA_FILE_TEMPLATE, objectMapper.toString(data));
    }

    private static Response toBinaryResponse(java.nio.file.Path file, boolean embedded) {
        return formResponse((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(out);
            } finally {
                Files.delete(file);
            }
        }, embedded);
    }

    static Response formResponse(Object form, boolean embedded) {
        return Response.ok(form, embedded ? MediaType.TEXT_HTML_TYPE : MediaType.TEXT_PLAIN_TYPE)
                .header("Content-Security-Policy", embedded ? FORM_CSP : "sandbox; default-src 'none'; frame-ancestors 'none'")
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-DNS-Prefetch-Control", "off")
                .header("X-Content-Type-Options", "nosniff")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
                .build();
    }

}
