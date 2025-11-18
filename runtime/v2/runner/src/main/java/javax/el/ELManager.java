/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;

/**
 * <p>Manages EL parsing and evaluation environment.  The ELManager maintains an
 * instance of ExpressionFactory and StandardELContext, for
 * parsing and evaluating EL expressions.</p>
 *
 * @since EL 3.0
 */
public class ELManager {

    private StandardELContext elContext;

    /**
     * Return the ExpressionFactory instance used for EL evaluations.
     * @return The ExpressionFactory
     */
    public static ExpressionFactory getExpressionFactory() {
        return ELUtil.getExpressionFactory();
    }

    /**
     * Return the ELContext used for parsing and evaluating EL expressions.
     * If there is currently no ELContext, a default instance of
     * StandardELContext is returned.
     *
     * @return The ELContext used for parsing and evaluating EL expressions..
     */
    public StandardELContext getELContext() {
        if (elContext == null) {
            elContext = new StandardELContext(getExpressionFactory());
        }
        return elContext;
    }

    /**
     * Set the ELContext used for parsing and evaluating EL expressions.
     * The supplied ELContext will not be modified, except for the context
     * object map.
     * @param context The new ELContext.
     * @return The previous ELContext, null if none.
     */
    public ELContext setELContext(ELContext context) {
        ELContext prev = elContext;
        elContext = new StandardELContext(context);
        return prev;
    }

    /**
     * Register a BeanNameResolver.
     * Construct a BeanNameELResolver with the BeanNameResolver and add it
     * to the list of ELResolvers.
     * Once registered, the BeanNameResolver cannot be removed.
     * @param bnr The BeanNameResolver to be registered.
     */
    public void addBeanNameResolver(BeanNameResolver bnr) {
        getELContext().addELResolver(new BeanNameELResolver(bnr));
    }

    /**
     * Add an user defined ELResolver to the list of ELResolvers.
     * Can be called multiple times.  The new ELResolver is
     * placed ahead of the default ELResolvers.  The list of the ELResolvers
     * added this way are ordered chronologically.
     * 
     * @param elr The ELResolver to be added to the list of ELResolvers in
     *     ELContext.
     * @see StandardELContext#addELResolver
     */
    public void addELResolver(ELResolver elr) {
        getELContext().addELResolver(elr);
    }

    /**
     * Maps a static method to an EL function.
     * @param prefix The namespace of the functions, can be "".
     * @param function The name of the function.  
     * @param meth The static method to be invoked when the function is used.
     */
    public void mapFunction(String prefix, String function, Method meth) {
        getELContext().getFunctionMapper().mapFunction(prefix, function, meth);
    }

    /** 
     * Assign a ValueExpression to an EL variable, replacing
     * any previous assignment to the same variable.
     * The assignment for the variable is removed if
     * the expression is <code>null</code>.
     *
     * @param variable The variable name
     * @param expression The ValueExpression to be assigned
     *        to the variable.
     */
    public void setVariable(String variable, ValueExpression expression) {
        getELContext().getVariableMapper().setVariable(variable, expression);
    }

    /**
     * Import a static field or method.  The class of the static member must be
     * loadable from the classloader, at class resolution time.
     * @param staticMemberName The full class name of the class to be imported
     * @throws ELException if the name is not a full class name.
     */
    public void importStatic(String staticMemberName) throws ELException {
        getELContext().getImportHandler().importStatic(staticMemberName);
    }

    /**
     * Import a class.  The imported class must be loadable from the classloader
     * at the expression evaluation time.
     * @param className The full class name of the class to be imported
     * @throws ELException if the name is not a full class name.
     */
    public void importClass(String className) throws ELException {
        getELContext().getImportHandler().importClass(className);
    }

    /**
     * Import a package.  At the expression evaluation time, the imported package
     * name will be used to construct the full class name, which will then be
     * used to load the class.  Inherently, this is less efficient than
     * importing a class.
     * @param packageName The package name to be imported
     */
    public void importPackage(String packageName) {
        getELContext().getImportHandler().importPackage(packageName);
    }

    /**
     * Define a bean in the local bean repository
     * @param name The name of the bean
     * @param bean The bean instance to be defined.  If null, the definition
     *        of the bean is removed.
     */
    public Object defineBean(String name, Object bean) {
        Object ret = getELContext().getBeans().get(name);
        getELContext().getBeans().put(name, bean);
        return ret;
    }

    /**
     * Register an evaluation listener.
     *
     * @param listener The evaluation listener to be added.
     */
    public void addEvaluationListener(EvaluationListener listener) {
        getELContext().addEvaluationListener(listener);
    }
}
