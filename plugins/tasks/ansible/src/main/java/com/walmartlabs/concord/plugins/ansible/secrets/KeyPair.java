package com.walmartlabs.concord.plugins.ansible.secrets;

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

import java.nio.file.Path;

/**
 * A type to use instead of v1 or v2 specific types.
 */
public class KeyPair {

    private final Path privateKey;
    private final Path publicKey;

    public KeyPair(Path privateKey, Path publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public Path privateKey() {
        return privateKey;
    }

    public Path publicKey() {
        return publicKey;
    }
}
