/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.el.parser;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.el.ELException;
import javax.el.FunctionMapper;
import javax.el.LambdaExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.el.ELClass;

import com.sun.el.lang.EvaluationContext;
import com.sun.el.util.MessageFactory;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class AstFunction extends SimpleNode {

    protected String localName = "";

    protected String prefix = "";

    public AstFunction(int id) {
        super(id);
    }

    public String getLocalName() {
        return localName;
    }

    public String getOutputName() {
        if (this.prefix.length() == 0) {
            return this.localName;
        } else {
            return this.prefix + ":" + this.localName;
        }
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public Class getType(EvaluationContext ctx)
            throws ELException {
        
        FunctionMapper fnMapper = ctx.getFunctionMapper();
        
        // quickly validate again for this request
        if (fnMapper == null) {
            throw new ELException(MessageFactory.get("error.fnMapper.null"));
        }
        Method m = fnMapper.resolveFunction(this.prefix, this.localName);
        if (m == null) {
            throw new ELException(MessageFactory.get("error.fnMapper.method",
                    this.getOutputName()));
        }
        return m.getReturnType();
    }

    /*
     * Find the object associated with the given name.  Return null if the
     * there is no such object.
     */
    private Object findValue(EvaluationContext ctx, String name) {
        Object value;
        // First check if this is a Lambda argument
        if (ctx.isLambdaArgument(name)) {
            return ctx.getLambdaArgument(name);
        }
        
        // Next check if this an EL variable
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(name);
            if (expr != null) {
                return expr.getValue(ctx.getELContext());
            }
        }
        // Check if this is resolvable by an ELResolver
        ctx.setPropertyResolved(false);
        Object ret = ctx.getELResolver().getValue(ctx, null, name);
        if (ctx.isPropertyResolved()) {
            return ret;
        }
        return null;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {

        // Check to see if a function is a bean that is a Lambdaexpression.
        // If so, invoke it.  Also allow for the case that a Lambda expression
        // can return another Lambda expression.
        if (prefix.length() == 0) {
            Object val = findValue(ctx, this.localName);
            // Check the case of repeated lambda invocation, such as f()()()

            if ((val != null) && (val instanceof LambdaExpression)) {
                for (int i = 0; i < this.children.length; i++) {
                    Object[] params = ((AstMethodArguments)this.children[i]).
                                                             getParameters(ctx);
                    if (! (val instanceof LambdaExpression)) {
                        throw new ELException(MessageFactory.get(
                            "error.function.syntax", getOutputName()));
                    }
                    val = ((LambdaExpression)val).invoke(ctx, params);
                }
                return val;
            }
        }

        FunctionMapper fnMapper = ctx.getFunctionMapper();
        
        Method m = null;
        if (fnMapper != null) {
            m = fnMapper.resolveFunction(this.prefix, this.localName);
        }
        if (m == null) {
            if (this.prefix.length() == 0 && ctx.getImportHandler() != null) {
                Class<?> c = null;;
                // Check if this is a constructor call for an imported class
                c = ctx.getImportHandler().resolveClass(this.localName);
                String methodName = null;
                if (c != null) {
                    methodName = "<init>";
                } else {
                    // Check if this is a imported static method
                    c = ctx.getImportHandler().resolveStatic(this.localName);
                    methodName = this.localName;;
                }
                if (c != null) {
                    // Use StaticFieldELResolver to invoke the constructor or the
                    // static method.
                    Object[] params =
                        ((AstMethodArguments)this.children[0]).getParameters(ctx);
                    return ctx.getELResolver().invoke(ctx, new ELClass(c),
                                   methodName, null, params);
                }
            }
            // quickly validate for this request
            if (fnMapper == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.null"));
            }
            throw new ELException(MessageFactory.get("error.fnMapper.method",
                    this.getOutputName()));
        }

        Class[] paramTypes = m.getParameterTypes();
        Object[] params =
            ((AstMethodArguments)this.children[0]).getParameters(ctx);
        Object result = null;
        for (int i = 0; i < params.length; i++) {
            try {
                params[i] = ctx.convertToType(params[i], paramTypes[i]);
            } catch (ELException ele) {
                throw new ELException(MessageFactory.get("error.function", this
                        .getOutputName()), ele);
            }
        }
        try {
            result = m.invoke(null, params);
        } catch (IllegalAccessException iae) {
            throw new ELException(MessageFactory.get("error.function", this
                    .getOutputName()), iae);
        } catch (InvocationTargetException ite) {
            throw new ELException(MessageFactory.get("error.function", this
                    .getOutputName()), ite.getCause());
        }
        return result;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    @Override
    public String toString()
    {
        return ELParserTreeConstants.jjtNodeName[id] + "[" + this.getOutputName() + "]";
    }
}
