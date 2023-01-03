package com.walmartlabs.concord.server.security;

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

import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ExceptionMapperSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Named
@Singleton
public class UnauthorizedExceptionMapper extends ExceptionMapperSupport<UnauthorizedException> {
    @Override
    protected Response convert(UnauthorizedException exception, String id) {
        return Response.status(Status.FORBIDDEN)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE)
                .entity(exception.getMessage())
                .build();
    }
}
