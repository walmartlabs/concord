package com.walmartlabs.concord.plugins.misc;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@Named("base64")
public class Base64TaskV2 implements Task {

    /**
     * Encodes bytes of a UTF-8 string as a base64 string.
     */
    public String encode(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(UTF_8));
    }

    /**
     * Decodes a base64-encoded string.
     */
    public String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), UTF_8);
    }
}
