package com.walmartlabs.concord.server.plugins.ansible.db;

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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.walmartlabs.concord.db.DatabaseChangeLogProvider;

import javax.inject.Named;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class DatabaseModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, DatabaseChangeLogProvider.class).addBinding().to(AnsibleDBChangeLogProvider.class);
    }
}
