package com.walmartlabs.concord.server.security.ldap;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.ldap.LdapContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class LdapUtils {

    private static final Logger log = LoggerFactory.getLogger(LdapUtils.class);

    public static Collection<String> getAllAttributeValues(Attribute attr) throws NamingException {
        Set<String> values = new HashSet<>();
        NamingEnumeration<?> ne = null;
        try {
            ne = attr.getAll();
            while (ne.hasMore()) {
                Object value = ne.next();
                if (value instanceof String) {
                    values.add((String) value);
                }
            }
        } finally {
            closeEnumeration(ne);
        }

        return values;
    }

    public static void closeContext(LdapContext ctx) {
        try {
            if (ctx != null) {
                ctx.close();
            }
        } catch (NamingException e) {
            log.error("closeContext -> error", e);
        }
    }

    public static void closeEnumeration(NamingEnumeration<?> ne) {
        try {
            if (ne != null) {
                ne.close();
            }
        } catch (NamingException e) {
            log.error("closeEnumeration -> {}, error", ne, e);
        }
    }

    private LdapUtils() {
    }
}
