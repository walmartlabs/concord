package com.walmartlabs.concord.process.loader.v2;

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


import com.walmartlabs.concord.runtime.model.SourceMap;
import com.walmartlabs.concord.runtime.model.Trigger;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class TriggerV2 implements Trigger, Serializable {

    private static final long serialVersionUID = 1L;

    private final com.walmartlabs.concord.runtime.v2.model.Trigger delegate;
    private final SourceMap sourceMap;

    public TriggerV2(com.walmartlabs.concord.runtime.v2.model.Trigger delegate) {
        this.delegate = delegate;
        this.sourceMap = SourceMap.from(delegate.location());
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Map<String, Object> arguments() {
        return delegate.arguments();
    }

    @Override
    public Map<String, Object> conditions() {
        return delegate.conditions();
    }

    @Override
    public Map<String, Object> configuration() {
        return delegate.configuration();
    }

    @Override
    public List<String> activeProfiles() {
        return delegate.activeProfiles();
    }

    @Override
    public SourceMap sourceMap() {
        return sourceMap;
    }
}
