package com.walmartlabs.concord.it.server;/*-
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

import com.walmartlabs.concord.client.*;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.directory.*;
import java.util.Properties;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeNotNull;

public class LdapIT extends AbstractServerIT {

    private static final String OBJECT_CLASS = "objectClass";
    private static final String COMMON_NAME = "cn";
    private static final String GROUP_OU = "ou=groups,dc=example,dc=org";
    private static final String USER_OU = "ou=users,dc=example,dc=org";
    private static DirContext ldapCtx;

    @BeforeClass
    public static void createLdapStructure() throws Exception {
        assumeNotNull(System.getenv("IT_LDAP_URL"));

        ldapCtx = createContext();

        //create organization units
        createLdapOrganizationalUnits(GROUP_OU, "groups");
        createLdapOrganizationalUnits(USER_OU, "users");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testLdapUserGroups() throws Exception {
        // create user in ldap
        String username = "tester";
        createLdapUser(username);

        // create group
        String groupName = "testerGroup";
        createLdapGroupWithUser(groupName, username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(username)
                .setType(CreateUserRequest.TypeEnum.LDAP));
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest()
                .setUsername(username)
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LDAP));

        setApiKey(cakr.getKey());

        // ---

        byte[] payload = archive(LdapIT.class.getResource("ldapInitiator").toURI());
        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        String groupDn = "cn=" + groupName + "," + GROUP_OU;
        assertLog(".*" + groupDn + ".*", ab);

    }

    public static DirContext createContext() throws Exception {
        String url = System.getenv("IT_LDAP_URL");
        String connectionType = "simple";
        String dn = "cn=admin,dc=example,dc=org";
        String credentials = "admin";

        Properties environment = new Properties();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, url);
        environment.put(Context.SECURITY_AUTHENTICATION, connectionType);
        environment.put(Context.SECURITY_PRINCIPAL, dn);
        environment.put(Context.SECURITY_CREDENTIALS, credentials);

        return new InitialDirContext(environment);
    }

    private static void createLdapOrganizationalUnits(String dn, String name) throws Exception {
        Attributes attributes = new BasicAttributes();

        Attribute ou = new BasicAttribute("ou", name);
        Attribute objectClass = new BasicAttribute(OBJECT_CLASS, "organizationalUnit");

        attributes.put(ou);
        attributes.put(objectClass);

        try {
            ldapCtx.createSubcontext(dn, attributes);
        } catch (NameAlreadyBoundException e) {
            System.err.println("createLdapOrganizationalUnits -> " + e.getMessage());
            // already exists, ignore
        }
    }

    private static void createLdapUser(String username) throws Exception {
        String dn = "uid=" + username + "," + USER_OU;
        Attributes attributes = new BasicAttributes();

        Attribute uid = new BasicAttribute("uid", username);
        Attribute cn = new BasicAttribute(COMMON_NAME, username);
        Attribute sn = new BasicAttribute("sn", username);

        Attribute objectClass = new BasicAttribute(OBJECT_CLASS);
        objectClass.add("top");
        objectClass.add("organizationalPerson");
        objectClass.add("person");
        objectClass.add("inetOrgPerson");

        attributes.put(uid);
        attributes.put(cn);
        attributes.put(sn);
        attributes.put(objectClass);

        try {
            ldapCtx.createSubcontext(dn, attributes);
        } catch (NameAlreadyBoundException e) {
            System.err.println("createLdapUser -> " + e.getMessage());
            // already exists, ignore
        }
    }

    private void createLdapGroupWithUser(String groupName, String username) throws Exception {
        String groupDn = "cn=" + groupName + "," + GROUP_OU;
        String userDn = "uid=" + username + "," + USER_OU;
        Attributes attributes = new BasicAttributes();

        Attribute cn = new BasicAttribute(COMMON_NAME, groupName);
        Attribute uniqueMember = new BasicAttribute("uniqueMember", userDn);
        Attribute objectClass = new BasicAttribute(OBJECT_CLASS, "groupOfUniqueNames");

        attributes.put(cn);
        attributes.put(uniqueMember);
        attributes.put(objectClass);

        try {
            ldapCtx.createSubcontext(groupDn, attributes);
        } catch (NameAlreadyBoundException e) {
            System.err.println("createLdapGroupWithUser -> " + e.getMessage());
            // already exists, ignore
        }
    }
}
