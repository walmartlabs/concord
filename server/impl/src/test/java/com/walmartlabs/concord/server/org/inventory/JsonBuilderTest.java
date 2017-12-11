package com.walmartlabs.concord.server.org.inventory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class JsonBuilderTest {

    @Test
    public void testArray() throws Exception {
        List<InventoryDataItem> items = ImmutableList.of(
            new InventoryDataItem("/a", 0, ImmutableList.of("1", "2", "3"))
        );

        Object result = JsonBuilder.build(items);
        assertEquals(ImmutableMap.of("a", ImmutableList.of("1", "2", "3")), result);
    }

    @Test
    public void testTwoNodes() throws Exception {
        List<InventoryDataItem> items = ImmutableList.of(
            new InventoryDataItem("/array", 0, ImmutableList.of("1", "2", "3")),
            new InventoryDataItem("/map", 0, ImmutableMap.of("k", "v"))
        );

        Object result = JsonBuilder.build(items);
        assertEquals(ImmutableMap.of("array", ImmutableList.of("1", "2", "3"),
                "map", ImmutableMap.of("k", "v")),
                result);
    }

    @Test
    public void testTwoNodesWithLongPath() throws Exception {
        List<InventoryDataItem> items = ImmutableList.of(
            new InventoryDataItem("/array", 0, ImmutableList.of("1", "2", "3")),
            new InventoryDataItem("/p1/p2/p3/map", 0, ImmutableMap.of("k", "v"))
        );

        Object result = JsonBuilder.build(items);
        assertEquals(ImmutableMap.of("array", ImmutableList.of("1", "2", "3"),
                "p1", ImmutableMap.of("p2", ImmutableMap.of("p3", ImmutableMap.of("map", ImmutableMap.of("k", "v"))))),
                result);
    }

    @Test
    public void testOverride() throws Exception {
        List<InventoryDataItem> items = ImmutableList.of(
            new InventoryDataItem("/map", 1, ImmutableMap.of("k", "v1")),
            new InventoryDataItem("/map", 2, ImmutableMap.of("k", "v2", "kk", "vv2"))
        );

        Object result = JsonBuilder.build(items);
        assertEquals(ImmutableMap.of("map", ImmutableMap.of("kk", "vv2", "k", "v1")),
                result);
    }

    @Test
    public void testOverride2() throws Exception {
        List<InventoryDataItem> items = ImmutableList.of(
            new InventoryDataItem("/map", 2, ImmutableMap.of("k", "v2", "kk", "vv2")),
            new InventoryDataItem("/map", 1, ImmutableMap.of("k", "v1"))
        );

        Object result = JsonBuilder.build(items);
        assertEquals(ImmutableMap.of("map", ImmutableMap.of("kk", "vv2", "k", "v1")),
                result);
    }
}
