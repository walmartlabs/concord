package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanELResolver;

public class MockBeanELResolver implements CustomBeanELResolver {

    @Override
    public Result invoke(Object base, String method, Object[] params) {
        if (base instanceof MockTask mockTask) {
            return mockTask.call(method, params);
        }
        return null;
//
//        if (base != null && "com.walmartlabs.concord.plugins.mock.VerifyTask$Mock".equals(base.getClass().getName())) {
//            paramTypes = new Class[2];
//            paramTypes[0] = String.class;
//            paramTypes[1] = List.class;
//
//            Object[] newParams = new Object[2];
//            newParams[0] = method;
//            newParams[1] = Arrays.asList(params);
//            method = "verify";
//            params = newParams;
//        }
    }
}
