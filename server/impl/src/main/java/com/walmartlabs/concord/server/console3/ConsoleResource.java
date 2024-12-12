package com.walmartlabs.concord.server.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static com.walmartlabs.concord.server.console3.ConsoleModule.BASE_PATH;

@Path(BASE_PATH)
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    /**
     * Redirect to /processes.
     */
    @GET
    @Path("/")
    public Response index(@Context UriInfo uriInfo) {
        var redirect = uriInfo.getBaseUriBuilder().path(BASE_PATH + "/processes").build();
        return Response.seeOther(redirect).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context UriInfo uriInfo) {
        SecurityUtils.logout();
        return index(uriInfo);
    }

    @GET
    @Path("/processes")
    public TemplateResponse processes(@Context UriInfo uriInfo,
                                      @Context HttpServletRequest request,
                                      @Context HttpServletResponse response) {

        return new TemplateResponse("processes", Map.of());
    }

    /**
     * Handles 404s.
     */
    @GET
    @Path("{path:.*}")
    public TemplateResponse serve() {

        return new TemplateResponse("404.html");
    }
}
