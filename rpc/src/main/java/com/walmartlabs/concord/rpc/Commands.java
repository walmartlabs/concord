package com.walmartlabs.concord.rpc;

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

import java.util.HashMap;
import java.util.Map;

public final class Commands {

    public static final String TYPE_KEY = "type";
    public static final String INSTANCE_ID_KEY = "instanceId";

    public static Map<String, Object> toMap(Command cmd) {
        Map<String, Object> m = new HashMap<>();
        m.put(TYPE_KEY, cmd.getType().toString());

        if (cmd instanceof CancelJobCommand) {
            m.put(INSTANCE_ID_KEY, ((CancelJobCommand) cmd).getInstanceId());
        }

        return m;
    }

    public static Command fromMap(Map<String, Object> m) {
        CommandType t = CommandType.valueOf((String) m.get(TYPE_KEY));

        switch (t) {
            case CANCEL_JOB: {
                String instanceId = (String) m.get(INSTANCE_ID_KEY);
                return new CancelJobCommand(instanceId);
            }
            default:
                throw new IllegalArgumentException("Unknown command type: " + t);
        }
    }

    private Commands() {
    }
}
