package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.io.Serializable;
import java.util.Objects;

public class ThreadId implements Serializable, Comparable<ThreadId> {

    private static final long serialVersionUID = -4315435932994207071L;

    private final long id;

    ThreadId(long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThreadId threadId = (ThreadId) o;
        return id == threadId.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public long id() {
        return id;
    }

    @Override
    public String toString() {
        return "ThreadId{" +
                "id=" + id +
                '}';
    }

    @Override
    public int compareTo(ThreadId o) {
        return Long.compare(id, o.id);
    }
}
