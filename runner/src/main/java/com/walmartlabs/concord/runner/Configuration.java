package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

public class Configuration {

    public static final String SERVER_HOST_KEY = "rpc.server.host";
    public static final String SERVER_PORT_KEY = "rpc.server.port";
    public static final String INSTANCE_ID_KEY = "instanceId";
    public static final String USER_DIR_KEY = "user.dir";

    private final String serverHost;
    private final int serverPort;
    private final String instanceId;
    private final String userDir;

    public Configuration() {
        this.serverHost = getEnv(SERVER_HOST_KEY, "localhost");
        this.serverPort = Integer.parseInt(getEnv(SERVER_PORT_KEY, "8101"));
        this.instanceId = getEnv(INSTANCE_ID_KEY);
        this.userDir = getEnv(USER_DIR_KEY);
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getUserDir() {
        return userDir;
    }

    private static String getEnv(String key) {
        return getEnv(key, null);
    }

    private static String getEnv(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }
}
