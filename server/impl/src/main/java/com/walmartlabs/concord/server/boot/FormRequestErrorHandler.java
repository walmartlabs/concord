package com.walmartlabs.concord.server.boot;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.console.ResponseTemplates;
import org.eclipse.jetty.http.MimeTypes;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public class FormRequestErrorHandler implements RequestErrorHandler {

    private static final String FORM_PATH_PREFIX = "/forms/";

    private final ResponseTemplates responseTemplates;

    @Inject
    public FormRequestErrorHandler(ResponseTemplates responseTemplates) {
        this.responseTemplates = responseTemplates;
    }

    @Override
    public boolean handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.getStatus() != 404) {
            return false;
        }

        String path = request.getPathInfo();
        if (path == null || !path.startsWith("/forms/") || !path.endsWith("/form/")) {
            return false;
        }

        String instanceId = getInstanceId(path);
        if (instanceId == null) {
            return false;
        }

        OutputStream out = response.getOutputStream();
        Map<String, Object> args = Collections.singletonMap("instanceId", instanceId);
        responseTemplates.formNotFound(out, args);

        response.setContentType(MimeTypes.Type.TEXT_HTML.asString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        return true;
    }

    private static String getInstanceId(String path) {
        int start = path.indexOf(FORM_PATH_PREFIX);

        if (start < 0) {
            return null;
        }

        int end = path.indexOf('/', start + FORM_PATH_PREFIX.length());
        if (end < 0) {
            return null;
        }

        return path.substring(start + FORM_PATH_PREFIX.length(), end);
    }
}
