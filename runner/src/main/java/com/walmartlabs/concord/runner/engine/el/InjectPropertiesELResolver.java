package com.walmartlabs.concord.runner.engine.el;

import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.runner.engine.DockerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.inject.Singleton;
import java.beans.FeatureDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

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
            Arrays.stream(clazz.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(InjectVariable.class))
                    .forEach(inject(context, base, method, isSingleton));
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static Consumer<Field> inject(ELContext context, Object base, Object method, boolean isSingleton) {
        return f -> {
            if (isSingleton) {
                log.warn("invoke ['{}', '{}'] -> @InjectVariable cannot be used in @Singleton tasks: '{}'", base, method, f.getName());
                return;
            }

            InjectVariable iv = f.getAnnotation(InjectVariable.class);

            try {
                Object variableValue = ResolverUtils.getVariable(context, iv.value());

                if (!f.isAccessible()) {
                    f.setAccessible(true);
                }
                f.set(base, variableValue);

//                PropertyUtils.setSimpleProperty(base, f.getName(), variableValue);
                log.debug("invoke ['{}', '{}'] -> set value '{}' for '{}'", base, method, f.getName(), variableValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
            }
        };
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
