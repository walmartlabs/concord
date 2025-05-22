package com.walmartlabs.concord.process.loader.v1;

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

import com.walmartlabs.concord.runtime.model.EffectiveProcessDefinitionProvider;
import com.walmartlabs.concord.runtime.model.Options;

import java.io.OutputStream;

public class EffectiveProcessDefinitionProviderV1 implements EffectiveProcessDefinitionProvider {

    private static final byte[] BANNER = "# the effective Concord YAML feature is currently supported only for the 'concord-v2' runtime".getBytes();

    @Override
    public void serialize(Options options, OutputStream out) throws Exception {
        out.write(BANNER);
        out.flush();
    }
}
