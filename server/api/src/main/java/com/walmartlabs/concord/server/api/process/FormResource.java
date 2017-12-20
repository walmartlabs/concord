package com.walmartlabs.concord.server.api.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Api(value = "Form", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public interface FormResource {

    @GET
    @ApiOperation("List the available forms")
    @Path("/{processInstanceId}/form")
    @Produces(MediaType.APPLICATION_JSON)
    List<FormListEntry> list(@ApiParam @PathParam("processInstanceId") UUID processInstanceId);

    /**
     * Returns the current state of a form instance.
     *
     * @param formInstanceId
     * @return
     */
    @GET
    @ApiOperation("Get the current state of a form")
    @Path("/{processInstanceId}/form/{formInstanceId}")
    @Produces(MediaType.APPLICATION_JSON)
    FormInstanceEntry get(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                          @ApiParam @PathParam("formInstanceId") @ConcordId String formInstanceId);

    /**
     * Submits form instance's data, potentially resuming a suspended process.
     *
     * @param formInstanceId
     * @param data
     * @return
     */
    @POST
    @ApiOperation("Submit form data")
    @Path("/{processInstanceId}/form/{formInstanceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    FormSubmitResponse submit(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                              @ApiParam @PathParam("formInstanceId") @ConcordId String formInstanceId,
                              @ApiParam Map<String, Object> data);

    /**
     * Submits form instance's data, potentially resuming a suspended process.
     *
     * @param formInstanceId
     * @param data
     * @return
     */
    @POST
    @ApiOperation("Submit form data")
    @Path("/{processInstanceId}/form/{formInstanceId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    FormSubmitResponse submit(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                              @ApiParam @PathParam("formInstanceId") @ConcordId String formInstanceId,
                              @ApiParam MultipartInput data);
}
