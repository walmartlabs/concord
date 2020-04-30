package com.walmartlabs.concord.server.boot;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface RequestErrorHandler {

    boolean handle(HttpServletRequest request, HttpServletResponse response) throws IOException;
}
