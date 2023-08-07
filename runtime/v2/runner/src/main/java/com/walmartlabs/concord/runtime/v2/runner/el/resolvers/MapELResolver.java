package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.sun.el.util.ReflectionUtil;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.ELContext;
import java.lang.reflect.Method;

public class MapELResolver extends javax.el.MapELResolver {

    private static final Logger log = LoggerFactory.getLogger(MapELResolver.class);

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Object result = super.getValue(context, base, property);
        if (result != null && context.isPropertyResolved()) {
            for (Method m : base.getClass().getMethods()) {
                log.info("method: {} -> {}", m, m.getAnnotation(SensitiveData.class));
            }
            Method m = ReflectionUtil.findMethod(base.getClass(), "get", new Class[]{Object.class}, null);

            log.info("RESULT: {}, method: {}", result, m);
            if (m != null) {
                SensitiveDataProcessor.process(result, m);
            }
        }
        return result;
    }
}
