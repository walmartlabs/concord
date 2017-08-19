package com.walmartlabs.concord.runner.engine.el;

import com.walmartlabs.concord.runner.engine.DockerTask;
import com.walmartlabs.concord.sdk.InjectVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.inject.Singleton;
import java.beans.FeatureDescriptor;
import java.lang.reflect.Field;
import java.util.Iterator;

public class InjectPropertiesELResolver extends ELResolver {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] paramValues) {
        if (base == null || method == null) {
            return null;
        }

        Class clazz = base.getClass();
        boolean isSingleton = clazz.isAnnotationPresent(Singleton.class);
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                String v = getAnnotationValue(f);
                if (v == null) {
                    continue;
                }

                inject(f, v, context, base, method, isSingleton);
            }

            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static String getAnnotationValue(Field f) {
        InjectVariable iv = f.getAnnotation(InjectVariable.class);
        if (iv != null) {
            return iv.value();
        } else {
            com.walmartlabs.concord.common.InjectVariable iv2 = f.getAnnotation(com.walmartlabs.concord.common.InjectVariable.class);
            if (iv2 != null) {
                return iv2.value();
            }
        }
        return null;
    }

    private static void inject(Field f, String value, ELContext context, Object base, Object method, boolean isSingleton) {
        if (isSingleton) {
            log.warn("invoke ['{}', '{}'] -> @InjectVariable cannot be used in @Singleton tasks: '{}'", base, method, f.getName());
            return;
        }

        try {
            Object variableValue = ResolverUtils.getVariable(context, value);

            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            f.set(base, variableValue);

            log.debug("invoke ['{}', '{}'] -> set value '{}' for '{}'", base, method, f.getName(), variableValue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
        }
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
}
