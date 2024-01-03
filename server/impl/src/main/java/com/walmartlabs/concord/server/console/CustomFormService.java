package com.walmartlabs.concord.server.console;

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
import com.walmartlabs.concord.server.sdk.validation.Validate;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@javax.ws.rs.Path("/api/service/custom_form")
public class CustomFormService implements Resource {

    private final ProcessStateManager stateManager;
    private final CustomFormServiceV1 customFormServiceV1;
    private final CustomFormServiceV2 customFormServiceV2;

    @Inject
    public CustomFormService(ProcessStateManager stateManager, CustomFormServiceV1 customFormServiceV1, CustomFormServiceV2 customFormServiceV2) {
        this.stateManager = stateManager;
        this.customFormServiceV1 = customFormServiceV1;
        this.customFormServiceV2 = customFormServiceV2;
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/start")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                            @PathParam("formName") String formName) {

        if (isV2(processInstanceId)) {
            return customFormServiceV2.startSession(processInstanceId, formName);
        } else {
            return customFormServiceV1.startSession(processInstanceId, formName);
        }
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultivaluedMap<String, String> data) {

        if (isV2(processInstanceId)) {
            return customFormServiceV2.continueSession(uriInfo, headers, processInstanceId, formName, data);
        } else {
            return customFormServiceV1.continueSession(uriInfo, headers, processInstanceId, formName, data);
        }
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultipartInput data) {

        if (isV2(processInstanceId)) {
            return customFormServiceV2.continueSession(uriInfo, headers, processInstanceId, formName, data);
        } else {
            return customFormServiceV1.continueSession(uriInfo, headers, processInstanceId, formName, data);
        }
    }

    private boolean isV2(UUID processInstanceId) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.JOB_FORMS_V2_DIR_NAME);
        return stateManager.exists(PartialProcessKey.from(processInstanceId), resource);
    }
}
