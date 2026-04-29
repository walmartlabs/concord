package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import com.walmartlabs.concord.runtime.v2.sdk.Context;

public final class StepOptionsUtils {

    public static boolean isDryRunReady(Context ctx, StepOptions options) {
        if (options == null) {
            return false;
        }

        Object result = options.meta().get("dryRunReady");
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }

        Object evalResult = ctx.eval(result.toString(), Object.class);
        if (evalResult == null) {
            return false;
        }
        if (evalResult instanceof Boolean) {
            return (Boolean) evalResult;
        }

        return Boolean.parseBoolean(evalResult.toString());
    }

    private StepOptionsUtils() {
    }
}
