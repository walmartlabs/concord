package com.walmartlabs.concord.server.audit;

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


public enum AuditObject {

    API_KEY,
    EXTERNAL_EVENT,
    JSON_STORE,
    JSON_STORE_DATA,
    JSON_STORE_QUERY,
    ORGANIZATION,
    POLICY,
    PROJECT,
    PROCESS,
    ROLE,
    SECRET,
    SYSTEM,
    TEAM,
    USER,
}
