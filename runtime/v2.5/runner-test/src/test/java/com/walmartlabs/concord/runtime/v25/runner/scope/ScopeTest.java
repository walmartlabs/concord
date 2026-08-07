package com.walmartlabs.concord.runtime.v25.runner.scope;

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

import com.walmartlabs.concord.runtime.v25.model.Values;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ScopeTest {

    @Test
    void distinguishesNullFromMissingAndIsolatesDottedWrites() {
        var root = Scope.root(Map.of("nested", Map.of("left", 1)), Set.of("default"),
                "default", false, false, null);
        root.set("nullable", null);
        var child = root.child("default");

        child.set("nested.right", 2);

        assertTrue(child.contains("nullable"));
        assertNull(child.get("nullable"));
        assertFalse(child.contains("missing"));
        assertEquals(Map.of("left", 1), root.get("nested"));
        assertEquals(Map.of("left", 1, "right", 2), child.get("nested"));
    }


    @Test
    void preservesLiteralPathSegmentsAndRejectsScalarDottedRoots() {
        var scope = Scope.root(Map.of("cfg", Map.of(), "scalar", 1), Set.of("default"),
                "default", false, false, null);

        scope.set(java.util.List.of("cfg", "a.b"), 2);

        assertEquals(2, ((Map<?, ?>) scope.get("cfg")).get("a.b"));
        var error = assertThrows(IllegalArgumentException.class, () -> scope.set("scalar.value", 2));
        assertTrue(error.getMessage().contains("scalar"));
        assertTrue(error.getMessage().contains(Integer.class.getName()));
    }
    @Test
    void rejectsAnInvalidCommitAtomically() {
        var scope = Scope.root(Map.of(), Set.of("default"), "default", false, false, null);
        var values = new LinkedHashMap<String, Object>();
        values.put("valid", 1);
        values.put("invalid", new Object());

        assertThrows(IllegalArgumentException.class, () -> scope.commit(values));

        assertFalse(scope.contains("valid"));
        assertFalse(scope.contains("invalid"));
    }
    @Test
    void forkClonesContainersWhilePreservingCyclesAndAliases() {
        var shared = new ArrayList<Object>();
        var container = new LinkedHashMap<String, Object>();
        var array = new Object[1];
        container.put("self", container);
        container.put("first", shared);
        container.put("second", shared);
        array[0] = array;
        var root = Scope.root(Map.of("container", container, "array", array), Set.of("default"),
                "default", false, false, null);

        var fork = root.fork();
        var rootContainer = (Map<?, ?>) root.get("container");
        var forkContainer = (Map<?, ?>) fork.get("container");
        var rootArray = (Object[]) root.get("array");
        var forkArray = (Object[]) fork.get("array");

        assertNotSame(rootContainer, forkContainer);
        assertSame(forkContainer, forkContainer.get("self"));
        assertSame(forkContainer.get("first"), forkContainer.get("second"));
        assertNotSame(rootArray, forkArray);
        assertSame(forkArray, forkArray[0]);
        assertTrue(Values.structurallyEqual(rootContainer, forkContainer));
        assertTrue(Values.structurallyEqual(rootArray, forkArray));
    }

    @Test
    void isolatesPrimitiveArraysAcrossForksAndComparesThemByContents() {
        var source = new int[]{1, 2};
        var root = Scope.root(Map.of("numbers", source), Set.of("default"), "default", false, false, null);
        var fork = root.fork();

        source[0] = 9;
        var rootNumbers = (int[]) root.get("numbers");
        var forkNumbers = (int[]) fork.get("numbers");
        forkNumbers[1] = 8;

        assertArrayEquals(new int[]{1, 2}, rootNumbers);
        assertArrayEquals(new int[]{1, 8}, forkNumbers);
        assertTrue(Values.structurallyEqual(rootNumbers, new int[]{1, 2}));
        assertFalse(Values.structurallyEqual(rootNumbers, forkNumbers));
    }

    @Test
    void rejectsNonSerializableNestedValuesButDefersFullSerialization() {
        var scope = Scope.root(Map.of(), Set.of("default"), "default", false, false, null);

        assertThrows(IllegalArgumentException.class,
                () -> scope.set("nested", Map.of("value", new Object())));

        var deferred = new BrokenSerializable();
        scope.set("deferred", deferred);
        assertSame(deferred, scope.get("deferred"));
    }

    private static final class BrokenSerializable implements Serializable {

        private final Object value = new Object();
    }
}
