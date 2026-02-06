package com.walmartlabs.concord.it.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import org.junit.jupiter.api.extension.*;

import java.util.function.Supplier;

public class SharedConcordExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(SharedConcordExtension.class);

    private static final String CONCORD_KEY = "concord";

    private final Supplier<ConcordRule> supplier;

    public SharedConcordExtension(Supplier<ConcordRule> supplier) {
        this.supplier = supplier;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ConcordRuleResource resource = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(CONCORD_KEY, k -> {
                    ConcordRule rule = supplier.get();
                    rule.start();
                    return new ConcordRuleResource(rule);
                }, ConcordRuleResource.class);

        context.getStore(NAMESPACE).put(CONCORD_KEY, resource.rule());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ConcordRule.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(CONCORD_KEY, ConcordRule.class);
    }

    private record ConcordRuleResource(ConcordRule rule) implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            rule.close();
        }
    }
}
