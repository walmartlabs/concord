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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.ClassSpace;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class SisuUtils {

    public static Injector createSisuInjector(ClassLoader cl, Module... modules) {
        Collection<Module> ms = new ArrayList<>();
        if (modules != null) {
            Collections.addAll(ms, modules);
        }

        ClassSpace cs = new URLClassSpace(cl);
        ms.add(new WireModule(new SpaceModule(cs, BeanScanning.CACHE)));

        return Guice.createInjector(ms);
    }

    private SisuUtils() {
    }
}
