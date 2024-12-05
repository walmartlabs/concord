package com.walmartlabs.concord.server.console3;

import org.thymeleaf.web.servlet.JavaxServletWebApplication;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

@WebListener
public class ThymeleafApp implements ServletContextListener {

    public static final String APP_KEY = ThymeleafApp.class.getName();

    public static JavaxServletWebApplication getInstance(HttpServletRequest request) {
        var servletContext = request.getServletContext();
        var app = (JavaxServletWebApplication) servletContext.getAttribute(APP_KEY);
        if (app == null) {
            throw new IllegalStateException("ThymeleafApp is not available in the given ServletContext. This is a bug.");
        }
        return app;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        var servletContext = sce.getServletContext();
        var app = JavaxServletWebApplication.buildApplication(servletContext);
        servletContext.setAttribute(APP_KEY, app);
    }
}
