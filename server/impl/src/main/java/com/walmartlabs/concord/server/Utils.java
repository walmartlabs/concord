package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.boot.resteasy.ExceptionMapperSupport;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.sdk.rest.Component;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import org.eclipse.jetty.ee8.servlet.ServletHolder;

import javax.servlet.Filter;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public final class Utils {

    public static String[] toArray(List<String> l) {
        if (l == null) {
            return null;
        }
        return l.toArray(new String[0]);
    }

    public static String[] toString(Enum<?>... e) {
        String[] as = new String[e.length];
        for (int i = 0; i < e.length; i++) {
            as[i] = e[i].toString();
        }
        return as;
    }

    public static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    public static <T> T unwrap(WrappedValue<T> v) {
        if (v == null) {
            return null;
        }

        return v.getValue();
    }

    public static void bindServletFilter(Binder binder, Class<? extends Filter> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, Filter.class).addBinding().to(klass);
    }

    public static void bindServletHolder(Binder binder, Class<? extends ServletHolder> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, ServletHolder.class).addBinding().to(klass);
    }

    public static void bindJaxRsResource(Binder binder, Class<? extends Resource> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(klass);
    }

    public static void bindSingletonBackgroundTask(Binder binder, Class<? extends BackgroundTask> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, BackgroundTask.class).addBinding().to(klass);
    }

    public static void bindScheduledTask(Binder binder, Class<? extends ScheduledTask> klass) {
        newSetBinder(binder, ScheduledTask.class).addBinding().to(klass);
    }

    public static void bindSingletonScheduledTask(Binder binder, Class<? extends ScheduledTask> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, ScheduledTask.class).addBinding().to(klass);
    }

    public static void bindExceptionMapper(Binder binder, Class<? extends ExceptionMapperSupport<?>> klass) {
        binder.bind(klass).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(klass);
        newSetBinder(binder, ExceptionMapper.class).addBinding().to(klass);
    }

    private Utils() {
    }
}
