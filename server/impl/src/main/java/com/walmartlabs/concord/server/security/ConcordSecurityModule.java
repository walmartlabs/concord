package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.walmartlabs.ollie.OllieServerBuilder;
import com.walmartlabs.ollie.guice.OllieSecurityModule;
import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.mgt.RememberMeManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.mgt.WebSecurityManager;

import javax.servlet.ServletContext;
import java.util.Collection;

public class ConcordSecurityModule extends OllieSecurityModule {

    public ConcordSecurityModule(OllieServerBuilder config, ServletContext servletContext) {
        super(config, servletContext);
    }

    @Override
    protected SecurityWebModule createSecurityWebModule(ServletContext servletContext) {
        return new OllieSecurityModule.SecurityWebModule(servletContext) {

            @Override
            protected void bindWebSecurityManager(AnnotatedBindingBuilder<? super WebSecurityManager> bind) {
                try {
                    bind.toConstructor(ConcordWebSecurityManager.class.getConstructor(Collection.class, RememberMeManager.class)).asEagerSingleton();
                } catch (NoSuchMethodException e) {
                    throw new ConfigurationException("This really shouldn't happen.  Either something has changed in Shiro, or there's a bug in ShiroModule.", e);
                }
            }
        };
    }

    public static class ConcordWebSecurityManager extends DefaultWebSecurityManager {
        public ConcordWebSecurityManager(Collection<Realm> realms, RememberMeManager rememberMeManager) {
            super(realms);
            this.setRememberMeManager(rememberMeManager);
        }
    }
}
