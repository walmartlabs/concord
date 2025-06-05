package com.walmartlabs.concord.runtime.v1.wrapper;

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

import java.io.Serializable;

public class SourceMapV1 implements SourceMap, Serializable {

    private static final long serialVersionUID = 1L;

    private final io.takari.bpm.model.SourceMap delegate;

    public SourceMapV1(io.takari.bpm.model.SourceMap delegate) {
        this.delegate = delegate;
    }

    @Override
    public String source() {
        return delegate.getSource();
    }

    @Override
    public int line() {
        return delegate.getLine();
    }

    @Override
    public int column() {
        return delegate.getColumn();
    }

    @Override
    public String description() {
        return delegate.getDescription();
    }
}
