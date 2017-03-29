package com.walmartlabs.concord.server.security;

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
