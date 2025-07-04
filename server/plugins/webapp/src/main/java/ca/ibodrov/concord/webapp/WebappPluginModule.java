package ca.ibodrov.concord.webapp;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import com.walmartlabs.concord.server.sdk.rest.ApiDescriptor;
import org.eclipse.jetty.ee8.servlet.ServletHolder;

import javax.inject.Named;
import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class WebappPluginModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, ApiDescriptor.class);
        newSetBinder(binder, HttpServlet.class);
        newSetBinder(binder, ServletHolder.class);

        newSetBinder(binder, Filter.class).addBinding().to(WebappFilter.class);
    }
}
