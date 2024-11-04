package com.walmartlabs.concord.plugins.mock.matcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

public abstract class AbstractMatcher<E1, E2> implements Matcher<E1, E2> {

    private final Class<E1> inputType;
    private final Class<E2> mockInputType;

    public AbstractMatcher(TypeReference<E1> inputTypeRef, TypeReference<E2> mockInputTypeRef) {
        this.inputType = inputTypeRef.getRawType();
        this.mockInputType = mockInputTypeRef.getRawType();
    }

    @Override
    public boolean canHandle(Object input, Object mockInput) {
        return inputType.isInstance(input) && mockInputType.isInstance(mockInput);
    }
}
