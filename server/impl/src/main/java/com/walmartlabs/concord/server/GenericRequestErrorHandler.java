package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.boot.RequestErrorHandler;
import com.walmartlabs.concord.server.console.ResponseTemplates;
import org.apache.http.HttpHeaders;
import org.eclipse.jetty.http.MimeTypes;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GenericRequestErrorHandler implements RequestErrorHandler {

    private final ResponseTemplates responseTemplates;

    @Inject
    public GenericRequestErrorHandler(ResponseTemplates responseTemplates) {
        this.responseTemplates = responseTemplates;
    }

    @Override
    public boolean handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> args = Map.of("statusCode", response.getStatus());

        OutputStream out = response.getOutputStream();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String accept = request.getHeader(HttpHeaders.ACCEPT);

        if (accept.toLowerCase().startsWith(MimeTypes.Type.APPLICATION_JSON.asString())) {
            response.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8.asString());
            out.write("{\"error\": \"An unexpected error occurred.\"}".getBytes(StandardCharsets.UTF_8));
        } else {
            responseTemplates.genericError(out, args);
            response.setContentType(MimeTypes.Type.TEXT_HTML.asString());
        }

        return true;
    }

}
