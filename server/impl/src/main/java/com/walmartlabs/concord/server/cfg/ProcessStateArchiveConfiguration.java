package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.ollie.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;

@Named
@Singleton
public class ProcessStateArchiveConfiguration implements Serializable {

    @Inject
    @Config("process.archive.period")
    private long period;

    @Inject
    @Config("process.archive.stalledCheckPeriod")
    private long stalledCheckPeriod;

    @Inject
    @Config("process.archive.stalledAge")
    private long stalledAge;

    @Inject
    @Config("process.archive.processAge")
    private long processAge;

    @Inject
    @Config("process.archive.uploadThreads")
    private int uploadThreads;

    @Inject
    @Config("process.archive.maxArchiveAge")
    private long maxArchiveAge;

    public boolean isEnabled() {
        return period > 0;
    }

    public long getPeriod() {
        return period;
    }

    public long getStalledCheckPeriod() {
        return stalledCheckPeriod;
    }

    public long getStalledAge() {
        return stalledAge;
    }

    public long getProcessAge() {
        return processAge;
    }

    public int getUploadThreads() {
        return uploadThreads;
    }

    public long getMaxArchiveAge() {
        return maxArchiveAge;
    }
}
