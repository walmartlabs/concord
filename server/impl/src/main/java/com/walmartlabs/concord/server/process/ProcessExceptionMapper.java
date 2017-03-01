package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ErrorMessage;
import org.sonatype.siesta.ExceptionMapperSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Named
@Singleton
public class ProcessExceptionMapper extends ExceptionMapperSupport<ProcessException> {
    @Override
    protected Response convert(ProcessException exception, String id) {
        String details = exception.getCause() != null ? exception.getCause().getMessage() : null;
        ErrorMessage msg = new ErrorMessage(exception.getMessage(), details);
        return Response.status(exception.getStatus())
                .entity(msg)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

}
