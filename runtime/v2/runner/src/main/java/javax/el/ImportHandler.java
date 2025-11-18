/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax.el;

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

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.lang.reflect.Modifier;

/**
 * Handles imports of class names and package names.  An imported package
 * name implicitly imports all the classes in the package.  A class that has
 * been imported can be used without its package name.
 * The name is resolved to its full (package and class) name
 * at evaluation time.
 */
public class ImportHandler {

    private Map<String, String> classNameMap = new HashMap<String, String>();
    private Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();
    private Map<String, String> staticNameMap = new HashMap<String, String>();
    private HashSet<String> notAClass = new HashSet<String>();
    private List<String> packages = new ArrayList<String>();

    {
        importPackage("java.lang");
    }

    /**
     * Import a static field or method.
     * @param name The static member name, including the full class name,
     *     to be imported
     * @throws ELException if the name does not include a ".".
     */
    public void importStatic(String name) throws ELException {
        int i = name.lastIndexOf('.');
        if (i <= 0) {
            throw new ELException(
                "The name " + name + " is not a full static member name");
        }
        String memberName = name.substring(i+1);
        String className = name.substring(0, i);
        staticNameMap.put(memberName, className);
    }

    /**
     * Import a class.
     * @param name The full class name of the class to be imported
     * @throws ELException if the name does not include a ".".
     */
    public void importClass(String name) throws ELException {
        int i = name.lastIndexOf('.');
        if (i <= 0) {
            throw new ELException(
                "The name " + name + " is not a full class name");
        }
        String className = name.substring(i+1);
        classNameMap.put(className, name);
    }

    /**
     * Import all the classes in a package.
     * @param packageName The package name to be imported
     */
    public void importPackage(String packageName) {
        packages.add(packageName);
    }

    /**
     * Resolve a class name.
     *
     * @param name The name of the class (without package name) to be resolved.
     * @return  If the class has been imported previously, with
     *     {@link #importClass} or {@link #importPackage}, then its
     *     Class instance. Otherwise <code>null</code>.
     * @throws ELException if the class is abstract or is an interface, or
     *     not public.
     */
    public Class<?> resolveClass(String name) {

        String className = classNameMap.get(name);
        if (className != null) {
            return resolveClassFor(className);
        }

        for (String packageName: packages) {
            String fullClassName = packageName + "." + name;
            Class<?>c = resolveClassFor(fullClassName);
            if (c != null) {
                classNameMap.put(name, fullClassName);
                return c;
            }
        }
        return null;
    }

    /**
     * Resolve a static field or method name.
     *
     * @param name The name of the member(without package and class name)
     *    to be resolved.
     * @return  If the field or method  has been imported previously, with
     *     {@link #importStatic}, then the class object representing the class that
     *     declares the static field or method.
     *     Otherwise <code>null</code>.
     * @throws ELException if the class is not public, or is abstract or
     *     is an interface.
     */
    public Class<?> resolveStatic(String name) {
        String className = staticNameMap.get(name);
        if (className != null) {
            Class<?> c = resolveClassFor(className);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private Class<?> resolveClassFor(String className) {
        Class<?> c = classMap.get(className);
        if (c != null) {
            return c;
        }
        c = getClassFor(className);
        if (c != null) {
            checkModifiers(c.getModifiers());
            classMap.put(className, c);
        }
        return c;
    }

    private Class<?> getClassFor(String className) {
        if (!notAClass.contains(className)) {
            try {
                return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException ex) {
                notAClass.add(className);
            }
        }
        return null;
    }

    private void checkModifiers(int modifiers) {
        if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)
                || ! Modifier.isPublic((modifiers))) {
            throw new ELException("Imported class must be public, and cannot be abstract or an interface");
        }
    }
}
