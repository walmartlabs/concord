package com.walmartlabs.concord.runtime.v2.runner.guice;

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

import com.google.inject.AbstractModule;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;

public class CurrentClasspathModule extends AbstractModule {

    @Override
    protected void configure() {
        ClassLoader cl = this.getClass().getClassLoader();
        // use sisu impl when this one released: https://github.com/eclipse/sisu.inject/commit/4790d3e28987ee4c2472d576e544c07028a85f42
        install(new SpaceModule(new URLClassSpace(cl), BeanScanning.GLOBAL_INDEX));
    }
}
