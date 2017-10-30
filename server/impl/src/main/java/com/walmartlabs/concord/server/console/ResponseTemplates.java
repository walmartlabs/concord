package com.walmartlabs.concord.server.console;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;
import java.io.OutputStreamWriter;
import java.util.Map;

public class ResponseTemplates {

    private final Mustache processFinished;
    private final Mustache badRequest;
    private final Mustache processError;

    public ResponseTemplates() {
        MustacheFactory mf = new DefaultMustacheFactory();
        processFinished = mf.compile("com/walmartlabs/concord/server/console/processFinished.html");
        badRequest = mf.compile("com/walmartlabs/concord/server/console/badRequest.html");
        processError = mf.compile("com/walmartlabs/concord/server/console/processError.html");
    }

    private ResponseBuilder html(Mustache m, ResponseBuilder r, Map<String, Object> args) {
        return r.type(MediaType.TEXT_HTML_TYPE)
                .entity((StreamingOutput) output -> {
                    try (OutputStreamWriter w = new OutputStreamWriter(output)) {
                        m.execute(w, args);
                    }
                });
    }

    public ResponseBuilder processFinished(ResponseBuilder r, Map<String, Object> args) {
        return html(processFinished, r, args);
    }

    public ResponseBuilder badRequest(ResponseBuilder r, Map<String, Object> args) {
        return html(badRequest, r, args);
    }

    public ResponseBuilder processError(ResponseBuilder r, Map<String, Object> args) {
        return html(processError, r, args);
    }
}
