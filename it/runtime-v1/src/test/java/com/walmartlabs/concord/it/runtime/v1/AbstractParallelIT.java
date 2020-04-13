package com.walmartlabs.concord.it.runtime.v1;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.Concord;
import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(ParallelRunner.class)
public abstract class AbstractParallelIT extends AbstractIT {

    @ClassRule
    public static Concord concord = new Concord()
            .localMode(Boolean.parseBoolean(System.getProperty("it.local.mode")))
            .streamServerLogs(true)
            .streamAgentLogs(true);

}
