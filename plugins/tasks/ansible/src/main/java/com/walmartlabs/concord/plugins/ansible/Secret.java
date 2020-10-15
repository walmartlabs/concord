package com.walmartlabs.concord.plugins.ansible;

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

import java.util.Map;

import static com.walmartlabs.concord.sdk.MapUtils.getString;

public class Secret {

    public static Secret from(Map<String, Object> auth) {
        String name = getString(auth, "name");
        if (name == null) {
            throw new IllegalArgumentException("Secret name is required ");
        }

        return new Secret(getString(auth, "org"), name, getString(auth, "password"));
    }

    private final String org;
    private final String name;
    private final String password;

    public Secret(String org, String name, String password) {
        this.org = org;
        this.name = name;
        this.password = password;
    }

    public String getOrg() {
        return org;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public final String toString() {
        return "Secret{" +
                "org='" + org + '\'' +
                ", name='" + name + '\'' +
                ", password='***" +
                '}';
    }
}
