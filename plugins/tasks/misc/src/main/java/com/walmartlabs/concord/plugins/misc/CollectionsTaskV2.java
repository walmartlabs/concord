package com.walmartlabs.concord.plugins.misc;

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

import com.google.common.collect.Lists;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides utility methods for working with collections
 */
@Named("collections")
@DryRunReady
public class CollectionsTaskV2 implements Task {

    /**
     * Concatenates multiple lists into a single list, preserving the order of elements.
     * Ignores any null lists.
     *
     * @param lists one or more lists to concatenate
     * @return a new list containing all elements from the input lists
     */
    @SafeVarargs
    public static <T> List<T> concat(List<T> ... lists) {
        return Arrays.stream(lists)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Concatenates multiple lists into a single set, eliminating duplicate elements.
     * Ignores any null lists.
     *
     * @param lists one or more lists to concatenate
     * @return a new set containing all unique elements from the input lists
     */
    @SafeVarargs
    public static <T> Set<T> concatAsSet(List<T> ... lists) {
        return Arrays.stream(lists)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Reverses the order of elements in the given list.
     *
     * @param list the list to reverse
     * @return a new list with the elements in reverse order
     */
    public static <T> List<T> reverse(List<T> list) {
        return Lists.reverse(list);
    }

    /**
     * Creates a new list of integers from 0 to the specified size (exclusive).
     * Each element in the list corresponds to its index.
     *
     * @param size the size of the list to create
     * @return a list of integers from 0 to (size - 1)
     */
    public static List<Integer> range(int size) {
        List<Integer> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(i);
        }
        return result;
    }

    /**
     * Creates a new instance of {@link LinkedHashMap} with {@code String} keys and {@code Object} values.
     *
     * @return a new, empty {@code LinkedHashMap<String, Object>}
     */
    public static Map<String, Object> newMap() {
        return new LinkedHashMap<>();
    }
}
