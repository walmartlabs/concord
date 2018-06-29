package com.walmartlabs.concord.server.org.inventory;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class InventoryQueryEntry implements Serializable {

    private final UUID id;

    private final UUID inventoryId;

    @NotNull
    @ConcordKey
    private final String inventoryName;

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final String text;

    @JsonCreator
    public InventoryQueryEntry(@JsonProperty("id") UUID id,
                               @JsonProperty("name") String name,
                               @JsonProperty("inventoryId") UUID inventoryId,
                               @JsonProperty("inventoryName") String inventoryName,
                               @JsonProperty("text") String text) {
        this.id = id;
        this.name = name;
        this.inventoryId = inventoryId;
        this.inventoryName = inventoryName;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public String getInventoryName() {
        return inventoryName;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "InventoryQueryEntry{" +
                "id=" + id +
                ", inventoryId='" + inventoryId + '\'' +
                ", inventoryName='" + inventoryName + '\'' +
                ", name='" + name + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
