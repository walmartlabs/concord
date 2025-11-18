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

/**
 * Resolves a bean by its known name.
 * This class can be extended to return a bean object given its name,
 * to set a value to an existing bean, or to create a bean with the value.
 * @see BeanNameELResolver
 *
 * @since EL 3.0
 */
public abstract class BeanNameResolver {
    /**
     * Returns whether the given name is resolved by the BeanNameResolver
     *
     * @param beanName The name of the bean.
     * @return true if the name is resolved by this BeanNameResolver; false
     *     otherwise.
     */
    public boolean isNameResolved(String beanName) {
        return false;
    }

    /**
     * Returns the bean known by its name.
     * @param beanName The name of the bean.
     * @return The bean with the given name.  Can be <code>null</code>.
     *     
     */
    public Object getBean(String beanName) {
        return null;
    }

    /**
     * Sets a value to a bean of the given name.
     * If the bean of the given name
     * does not exist and if {@link #canCreateBean} is <code>true</code>,
     * one is created with the given value.
     * @param beanName The name of the bean
     * @param value The value to set the bean to.  Can be <code>null</code>.
     * @throws PropertyNotWritableException if the bean cannot be
     *     modified or created.
     */
    public void setBeanValue(String beanName, Object value)
             throws PropertyNotWritableException {
        throw new PropertyNotWritableException();
    }

    /**
     * Indicates if the bean of the given name is read-only or writable
     * @param beanName The name of the bean
     * @return <code>true</code> if the bean can be set to a new value.
     *    <code>false</code> otherwise.
     */
    public boolean isReadOnly(String beanName) {
        return true;
    }

    /**
     * Allow creating a bean of the given name if it does not exist.
     * @param beanName The name of the bean
     * @return <code>true</code> if bean creation is supported
     *    <code>false</code> otherwise.
     */
    public boolean canCreateBean(String beanName) {
        return false;
    }
}
