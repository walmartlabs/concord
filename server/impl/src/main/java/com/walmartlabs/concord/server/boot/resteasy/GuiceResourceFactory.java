package com.walmartlabs.concord.server.boot.resteasy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.google.inject.Provider;
import org.jboss.resteasy.spi.*;

public class GuiceResourceFactory implements ResourceFactory {

    private final Provider<?> provider;
    private final Class<?> scannableClass;
    private PropertyInjector propertyInjector;

    public GuiceResourceFactory(Provider<?> provider, final Class<?> scannableClass) {
        this.provider = provider;
        this.scannableClass = scannableClass;
    }

    public Class<?> getScannableClass() {
        return scannableClass;
    }

    public void registered(ResteasyProviderFactory factory) {
        propertyInjector = factory.getInjectorFactory().createPropertyInjector(scannableClass, factory);
    }

    @Override
    public Object createResource(final HttpRequest request, final HttpResponse response, final ResteasyProviderFactory factory) {
        var resource = provider.get();
        var propertyStage = propertyInjector.inject(request, response, resource, true);
        return propertyStage == null ? resource : propertyStage
                .thenApply(v -> resource);
    }

    public void requestFinished(final HttpRequest request, final HttpResponse response, final Object resource) {
    }

    public void unregistered() {
    }
}
