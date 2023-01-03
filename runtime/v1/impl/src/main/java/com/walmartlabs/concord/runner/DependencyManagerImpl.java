package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.sdk.DependencyManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

@Named
@Singleton
public class DependencyManagerImpl implements DependencyManager {

    private final com.walmartlabs.concord.dependencymanager.DependencyManager delegate;

    @Inject
    public DependencyManagerImpl(com.walmartlabs.concord.dependencymanager.DependencyManager delegate) throws IOException {
        this.delegate = delegate;
    }

    @Override
    public Path resolve(URI uri) throws IOException {
        return delegate.resolveSingle(uri).getPath();
    }
}
