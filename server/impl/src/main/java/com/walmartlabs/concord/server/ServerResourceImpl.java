package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.api.PingResponse;
import com.walmartlabs.concord.server.api.ServerResource;
import com.walmartlabs.concord.server.api.VersionResponse;
import org.sonatype.siesta.Resource;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Named
public class ServerResourceImpl implements ServerResource, Resource {

    private final String version;
    private final String env;

    public ServerResourceImpl() {
        Properties props = new Properties();

        try (InputStream in = ServerResourceImpl.class.getResourceAsStream("version.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        this.version = props.getProperty("version");
        this.env = Utils.getEnv("CONCORD_ENV", "n/a");
    }

    public PingResponse ping() {
        return new PingResponse(true);
    }

    @Override
    public VersionResponse version() {
        return new VersionResponse(version, env);
    }
}
