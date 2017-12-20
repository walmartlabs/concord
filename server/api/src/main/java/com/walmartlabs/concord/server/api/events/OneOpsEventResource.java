package com.walmartlabs.concord.server.api.events;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Map;

@Api(value = "Events", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/events")
public interface OneOpsEventResource {

    /**
     * Process OneOps event.
     *
     * @param event event's data
     * @return
     */
    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    Response event(Map<String, Object> event);

    /**
     * Process OneOps event.
     *
     * @param in event's data
     * @return
     */
    @POST
    @Path("/oneops")
    @Consumes(MediaType.WILDCARD)
    Response event(InputStream in);
}
