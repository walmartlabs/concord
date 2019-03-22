package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.Collections;
import java.util.UUID;

@Named("checkpoint")
public class CheckpointTask implements Task {

    @Override
    public void execute(Context ctx) {
        String checkpoint = ContextUtils.assertString(ctx, "checkpointName");
        UUID checkpointId = UUID.randomUUID();
        ctx.setVariable("checkpointId", checkpointId.toString());
        ctx.suspend(checkpoint, Collections.singletonMap("checkpointId", checkpointId.toString()), false);
    }
}
