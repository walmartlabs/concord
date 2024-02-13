package com.walmartlabs.concord.server.process.form;

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
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Path("/api/v1/process")
@Tag(name = "Process Forms")
public class FormResource implements Resource {

    private final ProcessStateManager stateManager;
    private final FormResourceV1 formResourceV1;
    private final FormResourceV2 formResourceV2;

    @Inject
    public FormResource(ProcessStateManager stateManager, FormResourceV1 formResourceV1, FormResourceV2 formResourceV2) {
        this.stateManager = stateManager;
        this.formResourceV1 = formResourceV1;
        this.formResourceV2 = formResourceV2;
    }

    @GET
    @Path("/{processInstanceId}/form")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List the available forms", operationId = "listProcessForms")
    public List<FormListEntry> list(@PathParam("processInstanceId") UUID processInstanceId) {
        if (isV2(processInstanceId)) {
            return formResourceV2.list(processInstanceId);
        } else {
            return formResourceV1.list(processInstanceId);
        }
    }

    /**
     * Return the current state of a form instance.
     */
    @GET
    @Path("/{processInstanceId}/form/{formName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get the current state of a form", operationId = "getProcessForm")
    public FormInstanceEntry get(@PathParam("processInstanceId") UUID processInstanceId,
                                 @PathParam("formName") String formName) {

        if (isV2(processInstanceId)) {
            return formResourceV2.get(processInstanceId, formName);
        } else {
            return formResourceV1.get(processInstanceId, formName);
        }
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     */
    @POST
    @Path("/{processInstanceId}/form/{formName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Submit JSON form data", operationId = "submitForm")
    public FormSubmitResponse submit(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formName") String formName,
                                     Map<String, Object> data) {

        if (isV2(processInstanceId)) {
            return formResourceV2.submit(processInstanceId, formName, data);
        } else {
            return formResourceV1.submit(processInstanceId, formName, data);
        }
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     * The method must have a different {@code @Path} than {@link #submit(UUID, String, Map)} to avoid
     * conflicts in the Swagger spec/clients.
     */
    @POST
    @Path("/{processInstanceId}/form/{formName}/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Submit JSON form data", operationId = "submitFormAsMultipart")
    public FormSubmitResponse submit(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formName") String formName,
                                     @Parameter(schema = @Schema(type = "object", implementation = Object.class)) MultipartInput data) {

        if (isV2(processInstanceId)) {
            return formResourceV2.submit(processInstanceId, formName, data);
        } else {
            return formResourceV1.submit(processInstanceId, formName, data);
        }
    }

    private boolean isV2(UUID processInstanceId) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.JOB_FORMS_V2_DIR_NAME);
        return stateManager.exists(PartialProcessKey.from(processInstanceId), resource);
    }
}
