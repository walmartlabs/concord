package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

public class ResponseTemplates {

    private final Mustache processFinished;
    private final Mustache badRequest;
    private final Mustache processError;
    private final Mustache inProgressWait;
    private final Mustache formNotFound;

    public ResponseTemplates() {
        MustacheFactory mf = new DefaultMustacheFactory();
        processFinished = mf.compile("com/walmartlabs/concord/server/console/processFinished.html");
        badRequest = mf.compile("com/walmartlabs/concord/server/console/badRequest.html");
        processError = mf.compile("com/walmartlabs/concord/server/console/processError.html");
        inProgressWait = mf.compile("com/walmartlabs/concord/server/console/inProgress.html");
        formNotFound = mf.compile("com/walmartlabs/concord/server/console/formNotFound.html");
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

    public ResponseBuilder inProgressWait(ResponseBuilder r, Map<String, Object> args) {
        return html(inProgressWait, r, args);
    }

    public void formNotFound(OutputStream out, Map<String, Object> args) throws IOException {
        try (OutputStreamWriter w = new OutputStreamWriter(out)) {
            formNotFound.execute(w, args);
        }
    }
}
