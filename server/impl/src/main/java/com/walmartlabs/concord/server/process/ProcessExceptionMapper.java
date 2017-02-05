package com.walmartlabs.concord.server.process;

import org.sonatype.siesta.ExceptionMapperSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;

@Named
@Singleton
public class ProcessExceptionMapper extends ExceptionMapperSupport<ProcessException> {
    @Override
    protected Response convert(ProcessException exception, String id) {
        String details = exception.getCause() != null ? exception.getCause().getMessage() : null;
        Message msg = new Message(exception.getMessage(), details);
        return Response.status(exception.getStatus())
                .entity(msg)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    public static class Message implements Serializable {

        private final String message;
        private final String details;

        public Message(String message, String details) {
            this.message = message;
            this.details = details;
        }

        public String getMessage() {
            return message;
        }

        public String getDetails() {
            return details;
        }
    }
}
