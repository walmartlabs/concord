package com.walmartlabs.concord.runtime.v2.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.parser.SimpleOptions;

public class SuspendStep extends AbstractStep<SimpleOptions> {

    private static final long serialVersionUID = 1L;

    private final String event;

    public SuspendStep(Location location, String event, SimpleOptions options) {
        super(location, options);

        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "SuspendStep{" +
                "event='" + event + '\'' +
                '}';
    }
}
