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

import java.util.Iterator;
import java.beans.FeatureDescriptor;

/**
 * <p>An <code>ELResolver</code> for resolving user or container managed beans.</p>
 * <p>A {@link BeanNameResolver} is required for its proper operation.
 * The following example creates an <code>ELResolver</code> that 
 * resolves the name "bean" to an instance of MyBean.
 * <blockquote>
 * <pre>
 * ELResovler elr = new BeanNameELResolver(new BeanNameResolver {
 *    public boolean isNameResolved(String beanName) {
 *       return "bean".equals(beanName);
 *    }
 *    public Object getBean(String beanName) {
 *       return "bean".equals(beanName)? new MyBean(): null;
 *    }
 * });
 * </pre>
 * </blockquote>
 * </p>
 * @since EL 3.0
 */
public class BeanNameELResolver extends ELResolver {

    private BeanNameResolver beanNameResolver;

    /**
     * Constructor
     * @param beanNameResolver The {@link BeanNameResolver} that resolves a bean name.
     */
    public BeanNameELResolver(BeanNameResolver beanNameResolver) {
        this.beanNameResolver = beanNameResolver;
    }

    /**
     * If the base object is <code>null</code> and the property is a name
     * that is resolvable by the BeanNameResolver, returns the value
     * resolved by the BeanNameResolver.
     *
     * <p>If name is resolved by the BeanNameResolver, the
     * <code>propertyResolved</code> property of the <code>ELContext</code>
     * object must be set to <code>true</code> by this resolver, before
     * returning. If this property is not <code>true</code> after this
     * method is called, the caller should ignore the return value.</p>
     *
     * @param context The context of this evaluation.
     * @param base <code>null</code>
     * @param property The name of the bean.
     * @return If the <code>propertyResolved</code> property of
     *     <code>ELContext</code> was set to <code>true</code>, then
     *     the value of the bean with the given name. Otherwise, undefined.
     * @throws NullPointerException if context is <code>null</code>.
     * @throws ELException if an exception was thrown while performing
     *     the property or variable resolution. The thrown exception
     *     must be included as the cause property of this exception, if
     *     available.
     */
    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (base == null && property instanceof String) {
            if (beanNameResolver.isNameResolved((String) property)) {
                context.setPropertyResolved(base, property);
                return beanNameResolver.getBean((String) property);
            }
        }
        return null;
    }

    /**
     * If the base is null and the property is a name that is resolvable by
     * the BeanNameResolver, the bean in the BeanNameResolver is set to the
     * given value.
     *
     * <p>If the name is resolvable by the BeanNameResolver, or if the
     * BeanNameResolver allows creating a new bean,
     * the <code>propertyResolved</code> property of the
     * <code>ELContext</code> object must be set to <code>true</code>
     * by the resolver, before returning. If this property is not
     * <code>true</code> after this method is called, the caller can
     * safely assume no value has been set.</p>
     *
     * @param context The context of this evaluation.
     * @param base <code>null</code>
     * @param property The name of the bean
     * @param value The value to set the bean with the given name to.
     * @throws NullPointerException if context is <code>null</code>
     * @throws PropertyNotWritableException if the BeanNameResolver does not
     *     allow the bean to be modified.
     * @throws ELException if an exception was thrown while attempting to
     *     set the bean with the given name.  The thrown exception
     *     must be included as the cause property of this exception, if
     *     available.
     */
    @Override
    public void setValue(ELContext context, Object base, Object property,
                         Object value) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property instanceof String) {
            String beanName = (String) property;
            if (beanNameResolver.isNameResolved(beanName) ||
                    beanNameResolver.canCreateBean(beanName)) {
                beanNameResolver.setBeanValue(beanName, value);
                context.setPropertyResolved(base, property);
            }
        }
    }

    /**
     * If the base is null and the property is a name resolvable by
     * the BeanNameResolver, return the type of the bean.
     *
     * <p>If the name is resolvable by the BeanNameResolver,
     * the <code>propertyResolved</code> property of the
     * <code>ELContext</code> object must be set to <code>true</code>
     * by the resolver, before returning. If this property is not
     * <code>true</code> after this method is called, the caller can
     * safely assume no value has been set.</p>
     *
     * @param context The context of this evaluation.
     * @param base <code>null</code>
     * @param property The name of the bean.
     * @return If the <code>propertyResolved</code> property of
     *     <code>ELContext</code> was set to <code>true</code>, then
     *     the type of the bean with the given name. Otherwise, undefined.
     * @throws NullPointerException if context is <code>null</code>.
     * @throws ELException if an exception was thrown while performing
     *     the property or variable resolution. The thrown exception
     *     must be included as the cause property of this exception, if
     *     available.
     */
    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property instanceof String) {
            if (beanNameResolver.isNameResolved((String) property)) {
                context.setPropertyResolved(true);
                return beanNameResolver.getBean((String) property).getClass();
            }
        }
        return null;
    }

    /**
     * If the base is null and the property is a name resolvable by
     * the BeanNameResolver, attempts to determine if the bean is writable.
     *
     * <p>If the name is resolvable by the BeanNameResolver,
     * the <code>propertyResolved</code> property of the
     * <code>ELContext</code> object must be set to <code>true</code>
     * by the resolver, before returning. If this property is not
     * <code>true</code> after this method is called, the caller can
     * safely assume no value has been set.</p>
     *
     * @param context The context of this evaluation.
     * @param base <code>null</code>
     * @param property The name of the bean.
     * @return If the <code>propertyResolved</code> property of
     *     <code>ELContext</code> was set to <code>true</code>, then
     *     <code>true</code> if the property is read-only or
     *     <code>false</code> if not; otherwise undefined.
     * @throws NullPointerException if context is <code>null</code>.
     * @throws ELException if an exception was thrown while performing
     *     the property or variable resolution. The thrown exception
     *     must be included as the cause property of this exception, if
     *     available.
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base == null && property instanceof String) {
            if (beanNameResolver.isNameResolved((String) property)) {
                context.setPropertyResolved(true);
                return beanNameResolver.isReadOnly((String) property);
            }
        }
        return false;
    }

    /**
     * Always returns <code>null</code>, since there is no reason to 
     * iterate through a list of one element: bean name.
     * @param context The context of this evaluation.
     * @param base <code>null</code>.
     * @return <code>null</code>.
     */
    public Iterator<FeatureDescriptor> getFeatureDescriptors(
                                   ELContext context, Object base) {
        return null;
    }

    /**
     * Always returns <code>String.class</code>, since a bean name is a String.
     * @param context The context of this evaluation.
     * @param base <code>null</code>.
     * @return <code>String.class</code>.
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}
