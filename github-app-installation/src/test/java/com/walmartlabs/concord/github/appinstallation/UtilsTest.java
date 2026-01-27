package com.walmartlabs.concord.github.appinstallation;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.github.appinstallation.exception.GitHubAppException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.walmartlabs.concord.github.appinstallation.TestConstants.APP_INSTALL_CONTENT;
import static com.walmartlabs.concord.github.appinstallation.TestConstants.MAPPPER;
import static com.walmartlabs.concord.github.appinstallation.TestConstants.MOCK_APP_INSTALL_SECRET;
import static com.walmartlabs.concord.github.appinstallation.TestConstants.MOCK_STATIC_TOKEN_SECRET;
import static com.walmartlabs.concord.github.appinstallation.Utils.parseAppInstallation;
import static com.walmartlabs.concord.github.appinstallation.Utils.parseRawAppInstallation;
import static com.walmartlabs.concord.github.appinstallation.Utils.validateSecret;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

    @Test
    void testValidateSecret() {
        assertTrue(validateSecret(MOCK_APP_INSTALL_SECRET, MAPPPER));
        assertTrue(validateSecret(MOCK_STATIC_TOKEN_SECRET, MAPPPER));
        assertFalse(validateSecret(null, MAPPPER));
        assertFalse(validateSecret(Mockito.mock(UsernamePassword.class), MAPPPER));
        assertFalse(validateSecret(new BinaryDataSecret(null), MAPPPER));
        assertFalse(validateSecret(new BinaryDataSecret(new byte[]{}), MAPPPER));
        assertFalse(validateSecret(new BinaryDataSecret("\nmytoken".getBytes()), MAPPPER));
        assertFalse(validateSecret(new BinaryDataSecret("{\"hello\":\"world\"}".getBytes()), MAPPPER));
    }

    @Test
    void parseAppInstallation_ValidJson() {
        var o = parseAppInstallation(APP_INSTALL_CONTENT.getBytes(), MAPPPER);

        assertTrue(o.isPresent());
        var result = o.get();
        assertEquals("123", result.clientId());
    }

    @Test
    void parseAppInstallation_MissingElement() {
        var missingClientId = """
                {
                    "githubAppInstallation": {
                        "urlPattern": "(?<baseUrl>github.local)/.*",
                        "privateKey": "mock-key-data"
                    }
                }""";
        var data = missingClientId.getBytes();
        var ex = assertThrows(GitHubAppException.class, () -> parseAppInstallation(data, MAPPPER));

        assertTrue(ex.getMessage().contains("Invalid app installation definition"));
    }

    @Test
    void parseAppInstallation_OtherJson() {
        var unexpectedJson = "{ \"valid\": \"but not usable here\"}";
        var result = parseAppInstallation(unexpectedJson.getBytes(), MAPPPER);

        assertFalse(result.isPresent());
    }

    @Test
    void parseRawAppInstallation_NotJson() {
        var unexpectedJson = "justText";
        var result = parseRawAppInstallation(unexpectedJson.getBytes(), MAPPPER);

        assertNull(result);
    }

    @Test
    void parseRawAppInstallation_OtherJson() {
        var unexpectedJson = "{ \"valid\": \"but not usable here\"}";
        var result = parseRawAppInstallation(unexpectedJson.getBytes(), MAPPPER);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
