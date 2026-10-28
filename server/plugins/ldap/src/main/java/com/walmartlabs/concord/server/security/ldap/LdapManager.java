package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import javax.naming.NamingException;
import java.util.List;
import java.util.Set;

public interface LdapManager {

    List<LdapGroupSearchResult> searchGroups(String filter) throws NamingException;

    Set<String> getGroups(String username, String domain) throws NamingException;

    LdapPrincipal getPrincipal(String username, String domain) throws Exception;

    LdapPrincipal getPrincipalByDn(String dn) throws Exception;

    LdapPrincipal getPrincipalByMail(String email) throws Exception;
}
