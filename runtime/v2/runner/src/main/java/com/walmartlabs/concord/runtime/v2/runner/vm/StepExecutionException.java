package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;

public class StepExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Step step;

    public StepExecutionException(Step step, Exception cause) {
        super(cause);

        this.step = step;
    }

    public Step getStep() {
        return step;
    }

    @Override
    public String getMessage() {
        return Location.toErrorPrefix(step.getLocation()) + getCause().getMessage();
    }
}
