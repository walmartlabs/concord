package com.walmartlabs.concord.server.process.pipelines.processors.policy;

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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.process.Payload;

public interface PolicyApplier {

    void apply(Payload payload, PolicyEngine policyEngine) throws Exception;

    static String appendMsg(String msg, String s) {
        if (s == null) {
            return msg;
        }
        return msg + ": " + s;
    }
}
