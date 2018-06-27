package com.walmartlabs.concord.dependencymanager;

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


import java.io.Serializable;

public class MavenRepository implements Serializable {

    private final String id;
    private final String contentType;
    private final String url;
    private boolean snapshotEnabled;

    public MavenRepository(String id, String contentType, String url, boolean snapshotEnabled) {
        this.id = id;
        this.contentType = contentType;
        this.url = url;
        this.snapshotEnabled = snapshotEnabled;
    }

    public String getId() {
        return id;
    }

    public String getContentType() {
        return contentType;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    @Override
    public String toString() {
        return "MavenRepository{" +
                "id='" + id + '\'' +
                ", contentType='" + contentType + '\'' +
                ", url='" + url + '\'' +
                ", snapshotEnabled=" + snapshotEnabled +
                '}';
    }
}
