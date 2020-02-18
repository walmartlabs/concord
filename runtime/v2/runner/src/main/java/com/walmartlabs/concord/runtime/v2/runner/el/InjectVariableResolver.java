package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.InjectVariable;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.MethodNotFoundException;
import java.beans.FeatureDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class InjectVariableResolver extends ELResolver {

    private final BeanELResolver delegate = new BeanELResolver();

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] paramValues) {
        if (base == null || method == null) {
            return null;
        }

        List<Method> methods = findMethodWithInjections(base.getClass(), method.toString());

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        for (Method m : methods) {
            Class<?>[] newParamTypes = processParamTypes(m.getParameters(), paramTypes);
            if (newParamTypes == null) {
                continue;
            }
            Object[] newParams = processParams(context, m.getParameters(), paramValues);
            if (newParams == null) {
                continue;
            }

            try {
                return delegate.invoke(context, base, method, newParamTypes, newParams);
            } catch (MethodNotFoundException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return Object.class;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
    }

    private static Class<?>[] getTypesFromValues(Object[] values) {
        if (values == null) {
            return null;
        }

        Class<?>[] result = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                result[i] = null;
            } else {
                result[i] = values[i].getClass();
            }
        }
        return result;
    }

    private static Class<?>[] processParamTypes(Parameter[] methodParams, Class<?>[] originalParamTypes) {
        Class<?>[] result = new Class[methodParams.length];
        int originalParamTypesLength = originalParamTypes != null ? originalParamTypes.length : 0;
        int paramIndex = 0;
        for (int i = 0; i < methodParams.length; i++) {
            Parameter mp = methodParams[i];
            if (getInjectedVariableName(mp) != null) {
                result[i] = mp.getType();
            } else {
                if (paramIndex >= originalParamTypesLength) {
                    return null;
                }
                result[i] = originalParamTypes[paramIndex];
                paramIndex++;
            }
        }

        if (paramIndex < originalParamTypesLength) {
            return null;
        }

        return result;
    }

    private static Object[] processParams(ELContext context, Parameter[] methodParams, Object[] originalParams) {
        Object[] result = new Object[methodParams.length];
        int paramIndex = 0;
        int originalParamsLength = originalParams != null ? originalParams.length : 0;
        for (int i = 0; i < methodParams.length; i++) {
            Parameter mp = methodParams[i];
            String n = getInjectedVariableName(mp);
            if (n != null) {
                result[i] = ResolverUtils.getVariable(context, n);
            } else {
                if (paramIndex >= originalParamsLength) {
                    return null;
                }
                result[i] = originalParams[paramIndex];
                paramIndex++;
            }
        }

        if (paramIndex < originalParamsLength) {
            return null;
        }

        return result;
    }

    private static String getInjectedVariableName(Parameter p) {
        InjectVariable iv = p.getAnnotation(InjectVariable.class);
        if (iv != null) {
            return iv.value();
        }
        return null;
    }

    private static List<Method> findMethodWithInjections(Class<?> type, String name) {
        List<Method> candidates = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name))
                .collect(Collectors.toList());

        return candidates.stream()
                .map(m -> findMethodWithInjections(type, m))
                .filter(Objects::nonNull)
                .sorted((o1, o2) -> {
                    int result = -Integer.compare(getInjectedParamCount(o1), getInjectedParamCount(o2));
                    if (result == 0) {
                        return -Integer.compare(o1.getParameterCount(), o2.getParameterCount());
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }

    private static Method findMethodWithInjections(Class<?> type, Method m) {
        if (getInjectedParamCount(m) > 0) {
            return m;
        }

        Class<?>[] inf = type.getInterfaces();
        Method mp;
        for (Class<?> anInf : inf) {
            try {
                mp = anInf.getMethod(m.getName(), m.getParameterTypes());
                mp = findMethodWithInjections(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        Class<?> sup = type.getSuperclass();
        if (sup != null) {
            try {
                mp = sup.getMethod(m.getName(), m.getParameterTypes());
                mp = findMethodWithInjections(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }

    private static int getInjectedParamCount(Method m) {
        Parameter[] params = m.getParameters();
        if (params == null) {
            return 0;
        }

        int count = 0;
        for (Parameter p : params) {
            if (getInjectedVariableName(p) != null) {
                count++;
            }
        }
        return count;
    }
}
