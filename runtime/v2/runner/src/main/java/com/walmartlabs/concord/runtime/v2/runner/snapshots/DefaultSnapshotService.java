package com.walmartlabs.concord.runtime.v2.runner.snapshots;

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

import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import org.eclipse.sisu.Typed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named
@Typed
public class DefaultSnapshotService implements SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSnapshotService.class);

    @Override
    public void create(String name, Runtime runtime, State state) {
        log.info("create ['{}'] -> done", name);
    }
}
