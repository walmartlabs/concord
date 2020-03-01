package com.walmartlabs.concord.server.events.oneops;

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

import com.walmartlabs.concord.server.org.triggers.TriggersDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Prepares trigger conditions for OneOps v2 triggers.
 * The actual implementation is the same as for v1 (see {@link OneOpsTriggerV1Processor}.
 * The only reason it is split into two different @Named classes is to keep
 * the resource's code as close to GitHub's as possible.
 */
@Named
@Singleton
public class OneOpsTriggerV2Processor extends OneOpsTriggerProcessor {

    @Inject
    public OneOpsTriggerV2Processor(TriggersDao triggersDao) {
        super(triggersDao, 2);
    }
}
