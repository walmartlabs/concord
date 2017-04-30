package com.walmartlabs.concord.server;

import org.eclipse.jetty.servlet.ServletContextHandler;

public interface ServletConfigurer {

    void configure(ServletContextHandler servletHandler);
}
