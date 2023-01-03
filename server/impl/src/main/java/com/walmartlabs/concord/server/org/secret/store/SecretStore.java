package com.walmartlabs.concord.server.org.secret.store;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import org.jooq.DSLContext;

import java.util.UUID;

public interface SecretStore {

    boolean isEnabled();

    void store(DSLContext tx, UUID id, byte[] data);

    void delete(DSLContext tx, UUID id);

    byte[] get(UUID id);

    String getDescription();

    String getType();
}
