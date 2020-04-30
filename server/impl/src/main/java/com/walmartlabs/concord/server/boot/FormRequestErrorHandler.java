package com.walmartlabs.concord.server.boot;

import com.walmartlabs.concord.server.console.ResponseTemplates;
import org.eclipse.jetty.http.MimeTypes;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Named
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
