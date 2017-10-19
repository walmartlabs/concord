package com.walmartlabs.concord.server.api.inventory;

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
                               @JsonProperty("inventoryName") String inventoryName,
                               @JsonProperty("text") String text) {
        this.id = id;
        this.name = name;
        this.inventoryName = inventoryName;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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
                ", inventoryName='" + inventoryName + '\'' +
                ", name='" + name + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
