package com.walmartlabs.concord.console3.resources;

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

import com.walmartlabs.concord.console3.TemplateResponse;
import com.walmartlabs.concord.console3.UserContext;
import com.walmartlabs.concord.server.sdk.rest.Resource;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.util.Map;

@Path("/api/console3")
public class UserProfileResource implements Resource {

    @POST
    @Path("/click")
    public TemplateResponse click(@Context UserContext user) {
        return new TemplateResponse("click.jte", Map.of("name", user.username()));
    }
}
