package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public final class TestParams {

    static File testPropertiesFile = new File(System.getProperty("user.home"), ".concord/profile");
    static Properties testProperties;

    static {
        testProperties = new Properties();
        if (testPropertiesFile.exists()) {
            try (InputStream input = new FileInputStream(testPropertiesFile)) {
                testProperties.load(input);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String testParameter(String existingName, String profileName) {
        String value = System.getenv(existingName);
        return value != null ? value : testProperties.getProperty(profileName);
    }

    private static String assertTestParameter(String existingName, String profileName) {
        return Optional.ofNullable(testParameter(existingName, profileName))
                .orElseThrow(() -> new IllegalArgumentException("Missing test parameter: " + existingName + " (env var) or " + profileName + " (~/.concord/profile)"));
    }

    public static final String TEST_API_TOKEN = assertTestParameter("SLACK_TEST_API_TOKEN", "SLACK_BOT_API_TOKEN");
    public static final String TEST_USER_API_TOKEN = testParameter("SLACK_TEST_API_TOKEN", "SLACK_USER_API_TOKEN");
    public static final String TEST_PROXY_ADDRESS = testParameter("SLACK_TEST_PROXY_ADDRESS", "SLACK_PROXY_ADDRESS");
    public static final String TEST_INVALID_PROXY_ADDRESS = testParameter("SLACK_TEST_INVALID_PROXY_ADDRESS", "SLACK_INVALID_PROXY_ADDRESS");
    public static final int TEST_PROXY_PORT = Integer.parseInt(Optional.ofNullable(testParameter("SLACK_TEST_PROXY_PORT", "SLACK_PROXY_PORT")).orElse("-1"));
    public static final String TEST_CHANNEL = assertTestParameter("SLACK_TEST_CHANNEL", "SLACK_CHANNEL");

    private TestParams() {
    }
}
