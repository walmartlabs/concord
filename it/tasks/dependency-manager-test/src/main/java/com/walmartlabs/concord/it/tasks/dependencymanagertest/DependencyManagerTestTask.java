package com.walmartlabs.concord.it.tasks.dependencymanagertest;

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
import com.walmartlabs.concord.sdk.DependencyManager;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Named("dependencyManagerTest")
public class DependencyManagerTestTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DependencyManagerTestTask.class);

    private final DependencyManager dependencyManager;

    @Inject
    public DependencyManagerTestTask(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        String url = ContextUtils.assertString(ctx, "url");
        log.info("Fetching {}...", url);

        // check if it actually returns the same artifact
        Path p1 = dependencyManager.resolve(URI.create(url));
        Path p2 = dependencyManager.resolve(URI.create(url));
        if (!p1.equals(p2)) {
            throw new RuntimeException("Got different results: \n" + p1 + "\n" + p2);
        }

        String s = new String(Files.readAllBytes(p2));
        log.info("Got: {}", s);
    }
}
