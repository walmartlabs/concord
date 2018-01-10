package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;

@Named
@Singleton
public class ProcessStateConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConfiguration.class);

    public static final String MAX_FILE_SIZE_KEY = "MAX_FILE_SIZE";
    public static final String DEFAULT_MAX_FILE_SIZE = "67108864"; // 64Mb

    private final long maxFileSize;

    public ProcessStateConfiguration() {
        this.maxFileSize = Long.parseLong(Utils.getEnv(MAX_FILE_SIZE_KEY, DEFAULT_MAX_FILE_SIZE));
        log.info("init -> max file size: {} bytes", maxFileSize);
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
