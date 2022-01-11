package com.walmartlabs.concord.sdk;

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


public class ClientException extends Exception {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 2690318957293887651L;

    private final String instanceId;

    public ClientException(String message) {
        this(null, message, null);
    }

    public ClientException(String message, Throwable cause) {
        this(null, message, cause);
    }

    public ClientException(String instanceId, String message, Throwable cause) {
        super(message, cause);
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
