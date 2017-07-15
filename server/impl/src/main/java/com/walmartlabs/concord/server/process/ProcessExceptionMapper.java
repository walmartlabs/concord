package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ErrorMessage;
import org.sonatype.siesta.ExceptionMapperSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;

@Named
@Singleton
public class ProcessExceptionMapper extends ExceptionMapperSupport<ProcessException> {

    public static final String TRACE_ENABLED_KEY = "X-Concord-Trace-Enabled";

    @Context
    HttpHeaders headers;

    @Override
    protected Response convert(ProcessException e, String id) {
        String details = e.getCause() != null ? e.getCause().getMessage() : null;

        String stacktrace = null;
        if (traceEnabled()) {
            StringWriter w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            stacktrace = w.toString();
        }

        ErrorMessage msg = new ErrorMessage(e.getInstanceId(), e.getMessage(), details, stacktrace);
        return Response.status(e.getStatus())
                .entity(msg)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    private boolean traceEnabled() {
        String s = headers.getHeaderString(TRACE_ENABLED_KEY);
        return s != null && Boolean.parseBoolean(s);
    }
}
