package com.walmartlabs.concord.it.keywhiz;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.ITUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class KeywhizSecretIT {

    private ServerClient serverClient;

    @Before
    public void init() {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    @Test
    public void testPlainSecretSecret() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(serverClient.getClient());

        String orgName = "org_" + ITUtils.randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String secretName = "secret_" + ITUtils.randomString();
        byte[] content = "secret-content".getBytes();
        SecretOperationResponse r = addPlainSecret(orgName, secretName, true, "iddqd", content);

        SecretsApi secretsApi = new SecretsApi(serverClient.getClient());
        List<SecretEntry> secrets = secretsApi.list(orgName);
        assertEquals(1, secrets.size());
        SecretEntry entry = secrets.get(0);
        assertEquals(SecretEntry.StoreTypeEnum.KEYWHIZ, entry.getStoreType());
    }

    @Test
    public void testKeyPair() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(serverClient.getClient());

        String orgName = "org_" + ITUtils.randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String secretName = "secret_" + ITUtils.randomString();
        byte[] privateKey = ByteStreams.toByteArray(KeywhizSecretIT.class.getResourceAsStream("/com/walmartlabs/concord/it/keywhiz/keys/key"));
        byte[] publicKey = ByteStreams.toByteArray(KeywhizSecretIT.class.getResourceAsStream("/com/walmartlabs/concord/it/keywhiz/keys/key.pub"));

        generateKeyPair(orgName, secretName, false, null, privateKey, publicKey);

        SecretsApi secretsApi = new SecretsApi(serverClient.getClient());
        PublicKeyResponse publicKeyResponse = secretsApi.getPublicKey(orgName, secretName);

        assertEquals(new String(publicKey), publicKeyResponse.getPublicKey());
    }

    @Test
    public void testDeleteSecret() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(serverClient.getClient());

        String orgName = "org_" + ITUtils.randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String secretName = "secret_" + ITUtils.randomString();
        byte[] content = "secret-content".getBytes();
        SecretOperationResponse r = addPlainSecret(orgName, secretName, true, "iddqd", content);

        SecretsApi secretsApi = new SecretsApi(serverClient.getClient());
        secretsApi.delete(orgName, secretName);

        List<SecretEntry> secretEntries = secretsApi.list(orgName);
        assertEquals(0, secretEntries.size());

    }

    private SecretOperationResponse generateKeyPair(String orgName, String name, boolean generatePassword,
                                                    String storePassword, byte[] privateKey, byte[] publicKey) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("generatePassword", generatePassword);
        m.put("type", SecretEntry.TypeEnum.KEY_PAIR.toString());
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }
        m.put("private", privateKey);
        m.put("public", publicKey);

        m.put("storeType", SecretEntry.StoreTypeEnum.KEYWHIZ.toString());

        return serverClient.postSecret(orgName, m);
    }

    private SecretOperationResponse addPlainSecret(String orgName, String name, boolean generatePassword,
                                                   String storePassword, byte[] secret) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntry.TypeEnum.DATA.toString());
        m.put("generatePassword", generatePassword);
        m.put("data", secret);
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        m.put("storeType", SecretEntry.StoreTypeEnum.KEYWHIZ.toString());
        return serverClient.postSecret(orgName, m);
    }
}
