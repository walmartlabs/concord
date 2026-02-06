package com.walmartlabs.concord.it.server;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.directory.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LdapIT extends AbstractServerIT {

    private static final String OBJECT_CLASS = "objectClass";
    private static final String COMMON_NAME = "cn";
    private static final String GROUP_OU = "ou=groups,dc=example,dc=org";
    private static final String USER_OU = "ou=users,dc=example,dc=org";
    private static DirContext ldapCtx;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    @BeforeAll
    public static void createLdapStructure() throws Exception {
        assumeTrue(ConcordConfiguration.ldapUrl() != null);

        ldapCtx = createContext();

        //create organization units
        createLdapOrganizationalUnits(GROUP_OU, "groups");
        createLdapOrganizationalUnits(USER_OU, "users");
    }

    @Test
    public void testLdapUserGroups() throws Exception {
        // create user in ldap
        String username = "tester";
        createLdapUser(username);

        // create group
        String groupName = "testerGroup";
        createLdapGroupWithUser(groupName, username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(username)
                .userType(CreateApiKeyRequest.UserTypeEnum.LDAP));

        setApiKey(cakr.getKey());

        // ---

        byte[] payload = archive(LdapIT.class.getResource("ldapInitiator").toURI());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        String groupDn = "cn=" + groupName + "," + GROUP_OU;
        assertLog(".*" + groupDn + ".*", ab);
    }

    @Test
    public void testCaseInsensitive() throws Exception {
        String username = "testUser_" + randomString();
        createLdapUser(username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));

        UserEntry ue = usersApi.findByUsername(username.toLowerCase());
        assertNotNull(ue);
        assertEquals(ue.getName(), username.toLowerCase());
    }

    @Test
    void testDisableLdapUser() throws Exception {
        String username = "tester_" + randomString();
        createLdapUser(username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));

        UserEntry ue = usersApi.findByUsername(username);
        assertNotNull(ue);
        assertFalse(ue.getDisabled());
        assertFalse(ue.getPermanentlyDisabled());

        ue = usersApi.disableUser(ue.getId(), false);
        assertNotNull(ue);
        assertTrue(ue.getDisabled());
        assertFalse(ue.getPermanentlyDisabled());

        ue = usersApi.disableUser(ue.getId(), true);
        assertNotNull(ue);
        assertTrue(ue.getDisabled());
        assertTrue(ue.getPermanentlyDisabled());
    }

    @Test
    void testSubmitFormRunAsGroupWithApiKey() throws Exception {
        // create users in ldap
        String noGroupUser = "noGroupUser" + randomString();
        createLdapUser(noGroupUser);

        String username = "runAsUser" + randomString();
        createLdapUser(username);

        // create group
        String groupName = "RunAsGroup" + randomString();
        createLdapGroupWithUser(groupName, username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(noGroupUser)
                .type(CreateUserRequest.TypeEnum.LDAP));

        String noGroupApiKey = createApiKey(noGroupUser);
        String validUserApiKey = createApiKey(username);

        setApiKey(validUserApiKey);

        // --- execute form

        byte[] payload = archive(LdapIT.class.getResource("ldapFormRunAs").toURI());
        StartProcessResponse spr = start(Map.of(
                "archive", payload,
                "arguments.ldapGroupName", groupName
        ));
        assertNotNull(spr.getInstanceId());


        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // --- try to get with user not in group (expect no permission)

        ApiException noGroupEx = assertThrows(ApiException.class, () ->
                new ProcessFormsApi(getApiClientForKey(noGroupApiKey)).getProcessForm(pir.getInstanceId(), "myForm"));

        assertEquals(403, noGroupEx.getCode());
        assertTrue(noGroupEx.getMessage().contains("doesn't have the necessary permissions to resume process. Expected LDAP group(s) '[CN=RunAsGroup"));

        // --- get form with user in expected ldap group

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClientForKey(validUserApiKey));

        FormInstanceEntry form = formsApi.getProcessForm(pir.getInstanceId(), "myForm");

        assertEquals("myForm", form.getName());
        assertEquals(1, form.getFields().size());
        assertEquals("inputName", form.getFields().get(0).getName());
        assertEquals("string", form.getFields().get(0).getType());

        // --- submit form with user in expected ldap group

        formsApi.submitForm(pir.getInstanceId(), "myForm", Map.of("inputName", "testuser"));

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Submitted name: testuser.*", ab);
    }

    @Test
    void testSubmitFormRunAsGroupWithPassword() throws Exception {
        // create users in ldap
        String noGroupUser = "noGroupUser" + randomString();
        createLdapUser(noGroupUser);

        String username = "runAsUser" + randomString();
        createLdapUser(username);

        // create group
        String groupName = "RunAsGroup" + randomString();
        createLdapGroupWithUser(groupName, username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(noGroupUser)
                .type(CreateUserRequest.TypeEnum.LDAP));

        String validUserApiKey = createApiKey(username);

        setApiKey(validUserApiKey);

        // --- execute form

        byte[] payload = archive(LdapIT.class.getResource("ldapFormRunAs").toURI());
        StartProcessResponse spr = start(Map.of(
                "archive", payload,
                "arguments.ldapGroupName", groupName
        ));
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // --- try to get with user not in group (expect no permission)

        ApiException noGroupEx = assertThrows(ApiException.class, () ->
                getFormHttpClient(getApiClient().getBaseUrl(), pir.getInstanceId(), "myForm", noGroupUser, noGroupUser));

        assertEquals(403, noGroupEx.getCode());
        assertTrue(noGroupEx.getResponseBody().contains("doesn't have the necessary permissions to resume process. Expected LDAP group(s) '[CN=RunAsGroup"));

        // --- get form with user in expected ldap group

        FormInstanceEntry form = getFormHttpClient(getApiClient().getBaseUrl(), pir.getInstanceId(), "myForm", username, username);

        assertEquals("myForm", form.getName());
        assertEquals(1, form.getFields().size());
        assertEquals("inputName", form.getFields().get(0).getName());
        assertEquals("string", form.getFields().get(0).getType());

        // --- submit form with user in expected ldap group

        submitFormHttpClient(getApiClient().getBaseUrl(), pir.getInstanceId(), "myForm", Map.of("inputName", "testuser"), username, username);

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Submitted name: testuser.*", ab);
    }

    public static DirContext createContext() throws Exception {
        String url = ConcordConfiguration.ldapUrl();
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
        Attribute userPassword = new BasicAttribute("userPassword", username);

        Attribute objectClass = new BasicAttribute(OBJECT_CLASS);
        objectClass.add("top");
        objectClass.add("organizationalPerson");
        objectClass.add("person");
        objectClass.add("inetOrgPerson");

        attributes.put(uid);
        attributes.put(cn);
        attributes.put(sn);
        attributes.put(userPassword);
        attributes.put(objectClass);

        try {
            ldapCtx.createSubcontext(dn, attributes);
        } catch (NameAlreadyBoundException e) {
            System.err.println("createLdapUser -> " + e.getMessage());
            // already exists, ignore
        }
    }

    private static void createLdapGroupWithUser(String groupName, String username) throws Exception {
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

    private String createApiKey(String username) throws Exception {
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .username(username)
                .userType(CreateApiKeyRequest.UserTypeEnum.LDAP));

        return cakr.getKey();
    }

    private FormInstanceEntry getFormHttpClient(String baseUrl, UUID instanceId, String formName, String username, String password) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/process/" + instanceId + "/form/" + formName))
                .GET()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        HttpResponse<InputStream> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream is = resp.body()) {
            if (resp.statusCode() != 200) {
                throw new ApiException(resp.statusCode(), resp.headers(), new String(is.readAllBytes()));
            }

            return new ObjectMapper().readValue(resp.body(), FormInstanceEntry.class);
        }
    }

    private void submitFormHttpClient(String baseUrl, UUID instanceId, String formName, Map<String, Object> data, String username, String password) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String requestBody = mapper.writeValueAsString(data);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/process/" + instanceId + "/form/" + formName))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()))
                .build();

        HttpResponse<InputStream> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream is = resp.body()) {
            if (resp.statusCode() != 200) {
                throw new ApiException(resp.statusCode(), resp.headers(), new String(resp.body().readAllBytes()));
            }
        }
    }
}
