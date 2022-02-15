package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.changelog.ChangeProcessor;
import org.javers.core.commit.CommitMetadata;
import org.javers.core.diff.Change;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.*;
import org.javers.core.diff.changetype.container.ArrayChange;
import org.javers.core.diff.changetype.container.ContainerChange;
import org.javers.core.diff.changetype.container.ListChange;
import org.javers.core.diff.changetype.container.SetChange;
import org.javers.core.diff.changetype.map.MapChange;
import org.javers.core.metamodel.object.GlobalId;
import org.javers.core.metamodel.object.ValueObjectId;

import java.util.HashMap;
import java.util.Map;

public class DiffUtils {

    private static final String KEY_PREVIOUS = "prev";
    private static final String KEY_NEW = "new";

    public static Map<String, Object> compare(Object left, Object right) {
        Javers javers = JaversBuilder.javers().build();

        Diff diff = javers.compare(left, right);

        CustomChangeProcessor changeProcessor = new CustomChangeProcessor();
        javers.processChangeList(diff.getChanges(), changeProcessor);

        Map<String, Object> m = changeProcessor.result();
        removeIfEmpty(m, KEY_PREVIOUS);
        removeIfEmpty(m, KEY_NEW);
        return m;
    }

    private static void removeIfEmpty(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) {
            return;
        }

        if (v instanceof Map) {
            Map mv = (Map) v;
            if (mv.isEmpty()) {
                m.remove(k);
            }
        }
    }

    private static class CustomChangeProcessor implements ChangeProcessor<Map<String, Object>> {
        private final Map<String, Object> result = new HashMap<>();

        private CustomChangeProcessor() {
            result.put(KEY_PREVIOUS, new HashMap<String, Object>());
            result.put(KEY_NEW, new HashMap<String, Object>());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onValueChange(ValueChange valueChange) {
            GlobalId id = valueChange.getAffectedGlobalId();
            Map<String, Object> prevO = getPrevious();
            Map<String, Object> nextO = getNew();

            if (id instanceof ValueObjectId) {
                ValueObjectId idV = (ValueObjectId) id;
                String[] path = idV.getFragment().split("/");


                for (String p : path) {
                    if (valueChange.getLeft() != null) {
                        prevO.putIfAbsent(p, new HashMap<String, Object>());
                        prevO = (Map<String, Object>) prevO.get(p);
                    }

                    if (valueChange.getRight() != null) {
                        nextO.putIfAbsent(p, new HashMap<String, Object>());
                        nextO = (Map<String, Object>) nextO.get(p);
                    }
                }
            }

            if (valueChange.getLeft() != null) {
                prevO.put(valueChange.getPropertyName(), valueChange.getLeft());
            }

            if (valueChange.getRight() != null) {
                nextO.put(valueChange.getPropertyName(), valueChange.getRight());
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNewObject(NewObject newObject) {
            GlobalId id = newObject.getAffectedGlobalId();

            Map<String, Object> newObject2 = (new ObjectMapper()).convertValue(
                    newObject.getAffectedObject().orElse(null),
                    new TypeReference<Map<String, Object>>() {
                    });

            if (id instanceof ValueObjectId) {
                ValueObjectId idV = (ValueObjectId) id;
                String[] path = idV.getFragment().split("/");

                Map<String, Object> nextO = getNew();
                for (int n = 0; n < path.length - 1; n++) {
                    nextO.putIfAbsent(path[n], new HashMap<String, Object>());

                    Object o = nextO.get(path[n]);
                    if (!(o instanceof Map)) {
                        continue;
                    }

                    nextO = (Map<String, Object>) o;
                }

                nextO.put(path[path.length - 1], newObject2);
            } else {
                result.put(KEY_NEW, newObject2);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onObjectRemoved(ObjectRemoved objectRemoved) {
            GlobalId id = objectRemoved.getAffectedGlobalId();

            Map<String, Object> newObject2 = (new ObjectMapper()).convertValue(
                    objectRemoved.getAffectedObject().orElse(null),
                    new TypeReference<Map<String, Object>>() {
                    });

            if (id instanceof ValueObjectId) {
                ValueObjectId idV = (ValueObjectId) id;
                String[] path = idV.getFragment().split("/");

                Map<String, Object> prevO = getPrevious();
                for (int n = 0; n < path.length - 1; n++) {
                    prevO.putIfAbsent(path[n], new HashMap<String, Object>());

                    Object o = prevO.get(path[n]);
                    if (!(o instanceof Map)) {
                        continue;
                    }

                    prevO = (Map<String, Object>) o;
                }

                prevO.put(path[path.length - 1], newObject2);
            } else {
                result.put(KEY_PREVIOUS, newObject2);
            }
        }

        @Override
        public Map<String, Object> result() {
            return result;
        }


        @SuppressWarnings("unchecked")
        private Map<String, Object> getPrevious() {
            return (Map<String, Object>) result.get(KEY_PREVIOUS);
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> getNew() {
            return (Map<String, Object>) result.get(KEY_NEW);
        }

        @Override
        public void onCommit(CommitMetadata commitMetadata) {
        }

        @Override
        public void onAffectedObject(GlobalId globalId) {
        }

        @Override
        public void beforeChangeList() {
        }

        @Override
        public void afterChangeList() {
        }

        @Override
        public void beforeChange(Change change) {
        }

        @Override
        public void afterChange(Change change) {
        }

        @Override
        public void onPropertyChange(PropertyChange propertyChange) {
        }

        @Override
        public void onReferenceChange(ReferenceChange referenceChange) {
        }

        @Override
        public void onContainerChange(ContainerChange containerChange) {
        }

        @Override
        public void onSetChange(SetChange setChange) {
        }

        @Override
        public void onArrayChange(ArrayChange arrayChange) {
        }

        @Override
        public void onListChange(ListChange listChange) {
        }

        @Override
        public void onMapChange(MapChange mapChange) {
        }
    }
}

