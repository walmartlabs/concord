package com.walmartlabs.concord.common;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

/**
 * Almost a carbon copy of <a href="https://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/input/ClassLoaderObjectInputStream.html">ClassLoaderObjectInputStream</a>
 * from commons-io.
 */
public class ObjectInputStreamWithClassLoader extends ObjectInputStream {

    private final ClassLoader classLoader;

    public ObjectInputStreamWithClassLoader(InputStream in, ClassLoader classLoader) throws IOException {
        super(in);
        this.classLoader = classLoader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        try {
            return Class.forName(desc.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            // delegate to super class loader which can resolve primitives
            return super.resolveClass(desc);
        }
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        Class<?>[] interfaceClasses = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaceClasses[i] = Class.forName(interfaces[i], false, classLoader);
        }

        try {
            return Proxy.getProxyClass(classLoader, interfaceClasses);
        } catch (IllegalArgumentException e) {
            return super.resolveProxyClass(interfaces);
        }
    }
}
