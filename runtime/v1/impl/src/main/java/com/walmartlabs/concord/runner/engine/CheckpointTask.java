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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Named("checkpoint")
public class CheckpointTask implements Task, LogTagMetadataProvider {

    private static final Pattern PATTERN = Pattern.compile("^[0-9a-zA-Z][0-9a-zA-Z_@.\\-~ ]{1,128}$");

    @Override
    public void execute(Context ctx) {
        String checkpointName = ContextUtils.assertString(ctx, "checkpointName");
        if (!PATTERN.matcher(checkpointName).matches()) {
            throw new IllegalArgumentException("Invalid checkpoint name: " + checkpointName + ". " +
                    "If you're using an expression in the checkpoint's name please validate its correctness. " +
                    "Checkpoint names must start with a digit or a latin letter, the length must be between 2 and 128 characters. " +
                    "Can contain whitespace, minus (-), tilde (~), dot (.), underscore (_) and @ characters.");
        }

        UUID checkpointId = UUID.randomUUID();
        ctx.setVariable("checkpointId", checkpointId.toString());

        ctx.suspend(checkpointName, Collections.singletonMap("checkpointId", checkpointId.toString()), false);
    }

    @Override
    public Map<String, Object> createLogTagMetadata(Context ctx) {
        String checkpointName = (String) ctx.getVariable("checkpointName");
        return Collections.singletonMap("checkpointName", checkpointName);
    }
}
