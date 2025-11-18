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
import java.util.HashMap;
import java.lang.reflect.Method;

/**
 * A standard ELContext suitable for use in a stand alone environment.
 * This class provides a default implementation of an ELResolver that contains
 * a number of useful ELResolvers.  It also provides local repositories for
 * the FunctionMapper, VariableMapper, and BeanNameResolver.  
 *
 * @since EL 3.0
 */

public class StandardELContext extends ELContext {

    /*
     * The ELResolver for this ELContext.
     */
    private ELResolver elResolver;

    /*
     * The list of the custom ELResolvers added to the ELResolvers.
     * An ELResolver is added to the list when addELResolver is called.
     */
    private CompositeELResolver customResolvers;

    /*
     * The ELResolver implementing the query operators.
     */
    private ELResolver streamELResolver;

    /*
     * The FunctionMapper for this ELContext.
     */
    private FunctionMapper functionMapper;

    /*
     * The pre-confured init function map;
     */
    private Map<String, Method> initFunctionMap;

    /*
     * The VariableMapper for this ELContext.
     */
    private VariableMapper variableMapper;

    /*
     * If non-null, indicates the presence of a delegate ELContext.
     * When a Standard is constructed from another ELContext, there is no
     * easy way to get its private context map, therefore delegation is needed.
     */
    private ELContext delegate = null;
 
    /**
     * A bean repository local to this context
     */
    private Map<String, Object> beans = new HashMap<String, Object>();

    /**
     * Construct a default ELContext for a stand-alone environment.
     * @param factory The ExpressionFactory 
     */
    public StandardELContext(ExpressionFactory factory) {
        this.streamELResolver = factory.getStreamELResolver();
        initFunctionMap = factory.getInitFunctionMap();
    }

    /**
     * Construct a StandardELContext from another ELContext.
     * @param context The ELContext that acts as a delegate in most cases
     */
    public StandardELContext(ELContext context) {
        this.delegate = context;
        // Copy all attributes except map and resolved
        CompositeELResolver elr = new CompositeELResolver();
        elr.add(new BeanNameELResolver(new LocalBeanNameResolver()));
        customResolvers = new CompositeELResolver();
        elr.add(customResolvers);
        elr.add(context.getELResolver());
        elResolver = elr;

        functionMapper = context.getFunctionMapper();
        variableMapper = context.getVariableMapper();
        setLocale(context.getLocale());
    }

    @Override
    public void putContext(Class key, Object contextObject) {
        if (delegate !=null) {
            delegate.putContext(key, contextObject);
        } else {
            super.putContext(key, contextObject);
        }
    }

    @Override
    public Object getContext(Class key) {
        if (delegate !=null) {
            return delegate.getContext(key);
        } else {
            return super.getContext(key);
        }
    }

    /**
     * Construct (if needed) and return a default ELResolver.
     * <p>Retrieves the <code>ELResolver</code> associated with this context.
     * This is a <code>CompositeELResover</code> consists of an ordered list of
     * <code>ELResolver</code>s.
     * <ol>
     * <li>A {@link BeanNameELResolver} for beans defined locally</li>
     * <li>Any custom <code>ELResolver</code>s</li>
     * <li>An <code>ELResolver</code> supporting the collection operations</li>
     * <li>A {@link StaticFieldELResolver} for resolving static fields</li>
     * <li>A {@link MapELResolver} for resolving Map properties</li>
     * <li>A {@link ResourceBundleELResolver} for resolving ResourceBundle properties</li>
     * <li>A {@link ListELResolver} for resolving List properties</li>
     * <li>An {@link ArrayELResolver} for resolving array properties</li>
     * <li>A {@link BeanELResolver} for resolving bean properties</li>
     * </ol>
     * </p>
     * @return The ELResolver for this context.
     */
    @Override
    public ELResolver getELResolver() {
        if (elResolver == null) {
            CompositeELResolver resolver = new CompositeELResolver();
            customResolvers = new CompositeELResolver();
            resolver.add(customResolvers);
            resolver.add(new BeanNameELResolver(new LocalBeanNameResolver()));
            if (streamELResolver != null) {
                resolver.add(streamELResolver);
            }
            resolver.add(new StaticFieldELResolver());
            resolver.add(new MapELResolver());
            resolver.add(new ResourceBundleELResolver());
            resolver.add(new ListELResolver());
            resolver.add(new ArrayELResolver());
            resolver.add(new BeanELResolver());
            elResolver = resolver;
        }
        return elResolver;
    }

    /**
     * Add a custom ELResolver to the context.  The list of the custom
     * ELResolvers will be accessed in the order they are added.
     * A custom ELResolver added to the context cannot be removed.
     * @param cELResolver The new ELResolver to be added to the context
     */
    public void addELResolver(ELResolver cELResolver) {
        getELResolver();  // make sure elResolver is constructed
        customResolvers.add(cELResolver);
    }

    /**
     * Get the local bean repository
     * @return the bean repository
     */
    Map<String, Object> getBeans() {
        return beans;
    } 

    /**
     * Construct (if needed) and return a default FunctionMapper.
     * @return The default FunctionMapper
     */
    @Override
    public FunctionMapper getFunctionMapper() {
        if (functionMapper == null) {
            functionMapper = new DefaultFunctionMapper(initFunctionMap);
        }
        return functionMapper;
    }

    /**
     * Construct (if needed) and return a default VariableMapper() {
     * @return The default Variable
     */
    @Override
    public VariableMapper getVariableMapper() {
        if (variableMapper == null) {
            variableMapper = new DefaultVariableMapper();
        }
        return variableMapper;
    }

    private static class DefaultFunctionMapper extends FunctionMapper {

        private Map<String, Method> functions = null;

        DefaultFunctionMapper(Map<String, Method> initMap){
            functions = (initMap == null)?
                               new HashMap<String, Method>():
                               new HashMap<String, Method>(initMap);
        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            return functions.get(prefix + ":" + localName);
        }

    
        @Override
        public void mapFunction(String prefix, String localName, Method meth){
            functions.put(prefix + ":" + localName, meth);
        }
    }

    private static class DefaultVariableMapper extends VariableMapper {

        private Map<String, ValueExpression> variables = null;

        @Override
        public ValueExpression resolveVariable (String variable) {
            if (variables == null) {
                return null;
            }
            return variables.get(variable);
        }

        @Override
        public ValueExpression setVariable(String variable,
                                           ValueExpression expression) {
            if (variables == null) {
                variables = new HashMap<String, ValueExpression>();
            }
            ValueExpression prev = null;
            if (expression == null) {
                prev = variables.remove(variable);
            } else {
                prev = variables.put(variable, expression);
            }
            return prev;
        }
    }

    private class LocalBeanNameResolver extends BeanNameResolver {

        @Override
        public boolean isNameResolved(String beanName) {
            return beans.containsKey(beanName);
        }

        @Override
        public Object getBean(String beanName) {
            return beans.get(beanName);
        }

        @Override
        public void setBeanValue(String beanName, Object value) {
            beans.put(beanName, value);
        }

        @Override
        public boolean isReadOnly(String beanName) {
            return false;
        }

        @Override
        public boolean canCreateBean(String beanName) {
            return true;
        }
    }
}
  
