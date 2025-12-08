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

import com.walmartlabs.concord.juel.util.ReflectionUtil;

import javax.el.ELContext;

public class MapELResolver extends javax.el.MapELResolver {

    private final SensitiveDataProcessor sensitiveDataProcessor;

    public MapELResolver(SensitiveDataProcessor sensitiveDataProcessor) {
        this.sensitiveDataProcessor = sensitiveDataProcessor;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        var result = super.getValue(context, base, property);
        if (context.isPropertyResolved()) {
            var m = ReflectionUtil.findMethod(base.getClass(), "get", new Class[] {Object.class}, new Object[] {property});
            sensitiveDataProcessor.process(result, m);
        }
        return result;
    }
}
