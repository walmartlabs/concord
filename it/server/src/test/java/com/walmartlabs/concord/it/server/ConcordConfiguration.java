package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.it.common.BaseConcordConfiguration;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Path;
import java.util.List;

public final class ConcordConfiguration {

    private static final Path sharedDir = BaseConcordConfiguration.setupSharedDir("concord-server-it");
    private static volatile GenericContainer<?> ldapContainer;

    public static Path sharedDir() {
        return sharedDir;
    }

    public static String ldapUrl() {
        if (ldapContainer == null) {
            return null;
        }
        return "ldap://" + ldapContainer.getHost() + ":" + ldapContainer.getMappedPort(389);
    }

    public static ConcordRule configure() {
        BaseConcordConfiguration.writeSigningKey(sharedDir);

        ConcordRule concord = BaseConcordConfiguration.createBase()
                .pathToRunnerV1(null)
                .pathToRunnerV2("target/runner-v2.jar")
                .hostAccessible(true)
                .sharedContainerDir(sharedDir)
                .extraContainerSupplier(network -> {
                    @SuppressWarnings("resource")
                    GenericContainer<?> ldap = new GenericContainer<>(System.getProperty("ldap.image", "osixia/openldap:1.5.0"))
                            .withNetwork(network)
                            .withNetworkAliases("ldap")
                            .withExposedPorts(389)
                            .withEnv("LDAP_ORGANISATION", "Example")
                            .withEnv("LDAP_DOMAIN", "example.org")
                            .withEnv("LDAP_ADMIN_PASSWORD", "admin");
                    ldapContainer = ldap;

                    @SuppressWarnings("resource")
                    GenericContainer<?> dind = new GenericContainer<>(System.getProperty("dind.image", "docker:dind"))
                            .withNetwork(network)
                            .withNetworkAliases("dind")
                            .withPrivilegedMode(true)
                            .withEnv("DOCKER_TLS_CERTDIR", "");
                    return List.of(ldap, dind);
                })
                .extraConfigurationSupplier(() -> BaseConcordConfiguration.baseConfig() + """
                    concord-server {
                        secretStore {
                            serverPassword = "aGVsbG93b3JsZA=="
                            secretStoreSalt = "aGVsbG93b3JsZA=="
                            projectSecretSalt = "aGVsbG93b3JsZA=="
                        }
                        github {
                            secret = "12345"
                            useSenderLdapDn = true
                            disableReposOnDeletedRef = true
                        }
                        ldap {
                            url = "ldap://ldap:389"
                            searchBase = "dc=example,dc=org"
                            principalSearchFilter = "(cn={0})"
                            userSearchFilter = "(cn=*{0}*)"
                            returningAttributes = ["cn","memberof","objectClass","sn","uid"]
                            usernameProperty = "cn"
                            mailProperty = "mail"
                            systemUsername = "cn=admin,dc=example,dc=org"
                            systemPassword = "admin"
                        }
                        process {
                            checkLogPermissions = true
                            signingKeyPath = "%%sharedDir%%/signing.pem"
                        }
                    }
                    concord-agent {
                        workersCount = 7
                        capabilities = {
                            type = "test"
                        }
                    }
                    """.replaceAll("%%sharedDir%%", sharedDir().toString()));

        return BaseConcordConfiguration.applyMode(concord);
    }

    private ConcordConfiguration() {
    }
}
