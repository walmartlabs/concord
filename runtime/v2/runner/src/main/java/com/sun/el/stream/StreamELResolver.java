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
 *
 * @author Kin-man Chung
 */

package com.sun.el.stream;

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

import java.beans.FeatureDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Array;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.LambdaExpression;

/*
 * This ELResolver intercepts method calls to a Collections, to provide
 * support for collection operations. 
 */

public class StreamELResolver extends ELResolver {

    public Object invoke(final ELContext context,
                         final Object base,
                         final Object method,
                         final Class<?>[] paramTypes,
                         final Object[] params) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> c = (Collection<Object>)base;
            if ("stream".equals(method) && params.length == 0) {
                context.setPropertyResolved(true);
                return new Stream(c.iterator());
            }
        }
        if (base.getClass().isArray()) {
            if ("stream".equals(method) && params.length == 0) {
                context.setPropertyResolved(true);
                return new Stream(arrayIterator(base));
            }
        }
        return null;
    }

    private static Iterator<Object> arrayIterator(final Object base) {
        final int size = Array.getLength(base);
        return new Iterator<Object>() {
            int index = 0;
            boolean yielded;
            Object current;

            @Override
            public boolean hasNext() {
                if ((!yielded) && index < size) {
                    current = Array.get(base, index++);
                    yielded = true;
                }
                return yielded;
            }

            @Override
            public Object next() {
                yielded = false;
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
            
/*
    private LambdaExpression getLambda(Object obj, String method) {
        if (obj == null || ! (obj instanceof LambdaExpression)) {
            throw new ELException ("When calling " + method + ", expecting an " +
                "EL lambda expression, but found " + obj);
        }
        return (LambdaExpression) obj;
    }
*/
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    public void setValue(ELContext context, Object base, Object property,
                                  Object value) {
    }

    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return false;
    }

    public Iterator<FeatureDescriptor> getFeatureDescriptors(
                                            ELContext context,
                                            Object base) {
        return null;
    }

    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}
