package com.walmartlabs.concord.server.security;

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
