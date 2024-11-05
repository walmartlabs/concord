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

import java.util.Collection;
import java.util.Iterator;

public class CollectionMatcher extends AbstractMatcher<Collection<Object>, Collection<Object>> {

    public CollectionMatcher() {
        super(new TypeReference<>() {}, new TypeReference<>() {});
    }

    @Override
    public boolean matches(Collection<Object> input, Collection<Object> mockInput) {
        if (input.size() != mockInput.size()) {
            return false;
        }

        Iterator<Object> inputIt = input.iterator();
        Iterator<Object> mockIt = mockInput.iterator();
        while (inputIt.hasNext() && mockIt.hasNext()) {
            if (!ArgsMatcher.match(inputIt.next(), mockIt.next())) {
                return false;
            }
        }
        return true;
    }
}
