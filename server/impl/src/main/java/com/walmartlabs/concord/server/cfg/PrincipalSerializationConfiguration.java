package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.concord.config.Config;

import javax.inject.Inject;
import java.io.Serializable;

/**
 * Controls the write format of principal collections (process state, "remember me" cookies).
 *
 * <p>By default ({@code legacyWriteEnabled = false}) principal collections are written in the JSON snapshot
 * format, which requires a registered {@link com.walmartlabs.concord.server.sdk.security.PrincipalSerializer}
 * for every concrete principal class. Deserialization always accepts both the JSON format and the legacy
 * Java-serialization format, regardless of this flag.
 *
 * <p>Rolling upgrade contract:
 * <ol>
 * <li>Before deploying the first upgraded node, configure
 * {@code concord-server.principalSerialization.legacyWriteEnabled = true} for all upgraded nodes. Old nodes
 * keep their existing Java writer.</li>
 * <li>Keep principal classes Java-Serializable and present/compatible on every node during this phase. This
 * mode cannot make a new plugin class readable on an old node that lacks it.</li>
 * <li>After every reader is upgraded, set the flag to {@code false} and restart the nodes with their existing
 * restart procedure. New-version nodes can coexist with either write flag because both can read both
 * formats.</li>
 * <li>Non-Serializable plugin principals are supported only after all writers have left legacy mode. The
 * codec implementation alone is not enough while legacy writes remain enabled.</li>
 * <li>Returning the flag to {@code true} does not convert JSON already persisted or issued in cookies; it
 * does not make rollback to old binaries safe. No backfill or downgrade migrator is introduced.</li>
 * </ol>
 */
public class PrincipalSerializationConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("principalSerialization.legacyWriteEnabled")
    private boolean legacyWriteEnabled;

    public boolean isLegacyWriteEnabled() {
        return legacyWriteEnabled;
    }
}
