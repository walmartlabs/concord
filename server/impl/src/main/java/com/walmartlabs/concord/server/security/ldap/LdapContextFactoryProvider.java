package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import org.apache.shiro.realm.ldap.LdapContextFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.naming.NamingException;

public class LdapContextFactoryProvider implements Provider<LdapContextFactory> {

    private final LdapConfiguration cfg;

    @Inject
    public LdapContextFactoryProvider(LdapConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public LdapContextFactory get() {
        if (cfg.getDnsSRVName() != null) {
            ConcordDnsSrvLdapContextFactory factory = new ConcordDnsSrvLdapContextFactory(cfg);
            try {
                factory.refreshSRVList();
            } catch (NamingException e) {
                throw new RuntimeException("ConcordDnsSrvLdapContextFactory init error", e);
            }
            return factory;
        } else {
            return new ConcordLdapContextFactory(cfg);
        }
    }
}
