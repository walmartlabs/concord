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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * <p>Provides an API for using EL in a stand-alone environment.</p>
 * 
 * <p>This class provides a direct and simple interface for
 * <ul>
 *   <li>Evaluating EL expressions.</li>
 *   <li>Assigning values to beans or setting a bean property.</li>
 *   <li>Setting a {@link ValueExpression} to a EL variable.</li>
 *   <li>Defining a static method as an EL function.</li>
 *   <li>Defining an object instance as an EL name.
 * </ul>
 * 
 * <p>This API is not a replacement for the APIs in EL 2.2.  Containers that
 * maintains EL environments can continue to do so, without using this API.</p>
 * 
 * <p>For EL users who want to manipulate EL environments, like adding custom
 * {@link ELResolver}s, {@link ELManager} can be used.</p>
 *
 * <h3>Scope and Life Cycle</h3>
 * <p>Since it maintains the state of the EL environments,
 * <code>ELProcessor</code> is not thread safe.  In the simplest case,
 * an instance can be created and destroyed before and after evaluating
 * EL expressions.  A more general usage is to use an instance of
 * <code>ELProcessor</code> for a session, so that the user can configure the
 * EL evaluation environment for that session.</p>
 *
 * <h3>Automatic Bracketing of Expressions</h3>
 * <p>A note about the EL expressions strings used in the class.  The strings
 * allowed in the methods {@link ELProcessor#getValue},
 * {@link ELProcessor#setValue}, and {@link ELProcessor#setVariable} are
 * limited to non-composite expressions, i.e. expressions
 * of the form ${...} or #{...} only.  Also, it is not necessary (in fact not
 * allowed) to bracket the expression strings with ${ or #{ and } in these
 * methods: they will be automatically bracketed.  This reduces the visual
 * cluster, without any lost of functionalities (thanks to the addition of the
 * concatenation operator).
 *
 * <h3>Example</h3>
 * The following code snippet illustrates the use of ELProcessor to define
 * a bean and evaluate its property.
 * <blockquote>
 * <pre>
 *   ELProcessor elp = new ELProcessor();
 *   elp.defineBean("employee", new Employee("Charlie Brown"));
 *   String name = elp.eval("employee.name");
 * </pre>
 * </blockquote>
 * @since EL 3.0
 */

public class ELProcessor {

    private ELManager elManager = new ELManager();
    private ExpressionFactory factory = elManager.getExpressionFactory();

    /**
     * Return the ELManager used for EL processing.
     * @return The ELManager used for EL processing.
     */
    public ELManager getELManager() {
        return elManager;
    }

    /**
     * Evaluates an EL expression.
     * @param expression The EL expression to be evaluated.
     * @return The result of the expression evaluation.
     */
    public Object eval(String expression) {
        return getValue(expression, Object.class);
    }

    /**
     * Evaluates an EL expression, and coerces the result to the specified type.
     * @param expression The EL expression to be evaluated.
     * @param expectedType Specifies the type that the resultant evaluation
     *        will be coerced to.
     * @return The result of the expression evaluation.
     */
    public Object getValue(String expression, Class<?> expectedType) {
        ValueExpression exp = factory.createValueExpression(
                                  elManager.getELContext(),
                                  bracket(expression), expectedType);
        return exp.getValue(elManager.getELContext());
    }

    /**
     * Sets an expression with a new value. 
     * The target expression is evaluated, up to the last property resolution,
     * and the resultant (base, property) pair is set to the provided value.
     *
     * @param expression The target expression
     * @param value The new value to set.
     * @throws PropertyNotFoundException if one of the property
     *     resolutions failed because a specified variable or property
     *     does not exist or is not readable.
     * @throws PropertyNotWritableException if the final variable or
     *     property resolution failed because the specified
     *     variable or property is not writable.
     * @throws ELException if an exception was thrown while attempting to
     *     set the property or variable. The thrown exception
     *     must be included as the cause property of this exception, if
     *     available.
     */
    public void setValue(String expression, Object value) {
        ValueExpression exp = factory.createValueExpression(
                                  elManager.getELContext(),
                                  bracket(expression), Object.class);
        exp.setValue(elManager.getELContext(), value);
    }

    /**
     * Assign an EL expression to an EL variable.  The expression is parsed,
     * but not evaluated, and the parsed expression is mapped to the EL
     * variable in the local variable map.
     * Any previously assigned expression to the same variable will be replaced.
     * If the expression is <code>null</code>, the variable will be removed.
     * @param var The name of the variable.
     * @param expression The EL expression to be assigned to the variable.
     */
    public void setVariable(String var, String expression) {
        ValueExpression exp = factory.createValueExpression(
                                  elManager.getELContext(),
                                  bracket(expression), Object.class);
        elManager.setVariable(var, exp);
    }

    /**
     * Define an EL function in the local function mapper.
     * @param prefix The namespace for the function or "" for no namesapce.
     * @param function The name of the function.
     *    If empty (""), the method name is used as the function name.
     * @param className The full Java class name that implements the function.
     * @param method The name (specified without parenthesis) or the signature 
     *    (as in the Java Language Spec) of the static method that implements
     *    the function.  If the name (e.g. "sum") is given, the first declared
     *    method in class that matches the name is selected.  If the signature
     *    (e.g. "int sum(int, int)" ) is given, then the declared method
     *    with the signature is selected.
     *    
     * @throws NullPointerException if any of the arguments is null.
     * @throws ClassNotFoundException if the specified class does not exists.
     * @throws NoSuchMethodException if the method (with or without the
     *    signature) is not a declared method of the class, or if the method
     *    signature is not valid, or if the method is not a static method.
     */
    public void defineFunction(String prefix, String function,
                               String className,
                               String method)
            throws ClassNotFoundException, NoSuchMethodException {
        
        if (prefix == null || function == null || className == null
                || method == null) {
            throw new NullPointerException("Null argument for defineFunction");
        }
        
        Method meth = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if(loader == null)
            loader = getClass().getClassLoader();
        Class<?> klass = Class.forName(className, false, loader);
        int j = method.indexOf('(');
        if (j < 0) {
            // Just a name is given
            for (Method m: klass.getDeclaredMethods()) {
                if (m.getName().equals(method)) {
                    meth = m;
                }
            }
            if (meth == null) {
                throw new NoSuchMethodException("Bad method name: " + method);
            }
        } else {
            // method is the signature
            // First get the method name, ignore the return type
            int p = method.indexOf(' ');
            if (p < 0) {
                throw new NoSuchMethodException(
                    "Bad method singnature: " + method);
            }
            String methodName = method.substring(p+1, j).trim();
            // Extract parameter types
            p = method.indexOf(')', j+1);
            if (p < 0) {
                throw new NoSuchMethodException(
                    "Bad method singnature: " + method);
            }
            String[] params = method.substring(j+1, p).split(",");
            Class<?>[] paramTypes = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = toClass(params[i], loader);
            }
            meth = klass.getDeclaredMethod(methodName, paramTypes);
        }
        if (! Modifier.isStatic(meth.getModifiers())) {
            throw new NoSuchMethodException("The method specified in defineFunction must be static: " + meth);
        }
        if (function.equals("")) {
            function = method;
        }
        elManager.mapFunction(prefix, function, meth);
    }

    /**
     * Define an EL function in the local function mapper.
     * @param prefix The namespace for the function or "" for no namesapce.
     * @param function The name of the function.
     *    If empty (""), the method name is used as the function name.
     * @param method The <code>java.lang.reflect.Method</code> instance of
     *    the method that implements the function.
     * @throws NullPointerException if any of the arguments is null.
     * @throws NoSuchMethodException if the method is not a static method
     */
    public void defineFunction(String prefix, String function, Method method)
            throws NoSuchMethodException {
        if (prefix == null || function == null || method == null) {
            throw new NullPointerException("Null argument for defineFunction");
        }
        if (! Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("The method specified in defineFunction must be static: " + method);
        }
        if (function.equals("")) {
            function = method.getName();
       }
        elManager.mapFunction(prefix, function, method);
    }

    /**
     * Define a bean in a local bean repository, hiding other beans of the
     * same name.  
     * @param name The name of the bean
     * @param bean The bean instance to be defined.  If <code>null</code>,
     *   the name will be removed from the local bean repository.
     */
    public void defineBean(String name, Object bean) {
        elManager.defineBean(name, bean);
    }

    /**
     * Return the Class object associated with the class or interface with
     * the given name.
     */
    private static Class<?> toClass(String type, ClassLoader loader)
            throws ClassNotFoundException {

        Class<?> c = null;
        int i0 = type.indexOf('[');
        int dims = 0;
        if (i0 > 0) {
            // This is an array.  Count the dimensions
            for (int i = 0; i < type.length(); i++) {
                if (type.charAt(i) == '[')
                    dims++;
            }
            type = type.substring(0, i0);
        }

        if ("boolean".equals(type))
            c = boolean.class;
        else if ("char".equals(type))
            c = char.class;
        else if ("byte".equals(type))
            c =  byte.class;
        else if ("short".equals(type))
            c = short.class;
        else if ("int".equals(type))
            c = int.class;
        else if ("long".equals(type))
            c = long.class;
        else if ("float".equals(type))
            c = float.class;
        else if ("double".equals(type))
            c = double.class;
        else
            c = loader.loadClass(type);

        if (dims == 0)
            return c;

        if (dims == 1)
            return java.lang.reflect.Array.newInstance(c, 1).getClass();

        // Array of more than i dimension
        return java.lang.reflect.Array.newInstance(c, new int[dims]).getClass();
    }

    private String bracket(String expression) {
        return "${" + expression + '}';
    }
}

