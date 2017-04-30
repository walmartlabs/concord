package com.walmartlabs.concord.server;

import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * Created by ibodrov on 28/04/17.
 */
public interface ServletConfigurer {
    void configure(ServletContextHandler servletHandler);
}
