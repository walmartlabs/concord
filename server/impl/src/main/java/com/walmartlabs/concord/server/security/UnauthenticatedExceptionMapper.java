package com.walmartlabs.concord.server.security;

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

import org.apache.shiro.authz.UnauthenticatedException;
import org.sonatype.siesta.ExceptionMapperSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Named
@Singleton
public class UnauthenticatedExceptionMapper extends ExceptionMapperSupport<UnauthenticatedException> {

    @Override
    protected Response convert(UnauthenticatedException exception, String id) {
        return Response.status(Status.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE)
                .entity(exception.getMessage())
                .build();
    }
}
