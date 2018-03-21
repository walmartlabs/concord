package com.walmartlabs.concord.server.console;

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
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.UUID;

@Path("/api/service/custom_form")
public interface CustomFormService {

    @POST
    @Path("{processInstanceId}/{formInstanceId}/start")
    @Produces(MediaType.APPLICATION_JSON)
    FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formInstanceId") @ConcordId String formInstanceId);

    @POST
    @Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Response continueSession(@Context UriInfo uriInfo,
                             @Context HttpHeaders headers,
                             @PathParam("processInstanceId") UUID processInstanceId,
                             @PathParam("formInstanceId") @ConcordId String formInstanceId,
                             MultivaluedMap<String, String> data);

    @POST
    @Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response continueSession(@Context UriInfo uriInfo,
                             @Context HttpHeaders headers,
                             @PathParam("processInstanceId") UUID processInstanceId,
                             @PathParam("formInstanceId") @ConcordId String formInstanceId,
                             MultipartInput data);
}
