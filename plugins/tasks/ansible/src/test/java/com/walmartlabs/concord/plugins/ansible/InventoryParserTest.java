package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class InventoryParserTest extends AbstractTest {

    @Test
    public void testEmptyInventory() {
        try (InputStream is = InventoryParserTest.class.getResourceAsStream("inventory_empty.json")) {
            AnsibleInventoryParser parser = new AnsibleInventoryParser();
            int hosts = parser.countInventoryHosts(is);
            assertEquals(0, hosts);

        } catch (Exception e) {
            fail("Failed to parse JSON inventory.");
        }
    }

    @Test
    public void testBasicInventory() {
        try (InputStream is = InventoryParserTest.class.getResourceAsStream("inventory_basic.json")) {
            AnsibleInventoryParser parser = new AnsibleInventoryParser();
            int hosts = parser.countInventoryHosts(is);
            assertEquals(1, hosts);

        } catch (Exception e) {
            fail("Failed to parse JSON inventory.");
        }
    }

    @Test
    public void testComplexInventory() {
        try (InputStream is = InventoryParserTest.class.getResourceAsStream("inventory_complex.json")) {
            AnsibleInventoryParser parser = new AnsibleInventoryParser();
            int hosts = parser.countInventoryHosts(is);
            assertEquals(6, hosts);

        } catch (Exception e) {
            fail("Failed to parse JSON inventory.");
        }
    }

    @Test
    public void testHugeInventory() {
        try (JsonInventoryInputStream is = new JsonInventoryInputStream(500_000)) {
            AnsibleInventoryParser parser = new AnsibleInventoryParser();
            int hosts = parser.countInventoryHosts(is);
            assertEquals(500_000, hosts);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            fail("Failed to parse JSON inventory.");
        }
    }

    private enum StreamGeneratorPhase {
        PRE,
        DYNAMIC,
        POST
    }

    /**
     * Produces an InputStream of JSON text. Useful for dynamically generating
     * very large inventories
     */
    private static class JsonInventoryInputStream extends InputStream {
        static final String pre = "{\"ungrouped\":{\"hosts\":[";
        static final String post = "]}}";
        static final String hostPattern = "\"host-%d\"%s";

        StreamGeneratorPhase phase;
        byte[] buf;
        int bufIndex;
        int index;
        int max;

        JsonInventoryInputStream(int hostCount) {
            this.phase = StreamGeneratorPhase.PRE;
            this.buf = pre.getBytes(StandardCharsets.UTF_8);
            this.max = hostCount;
        }

        private static String generateHost(long index, long max) {
            return String.format(hostPattern, index, index < max-1 ? "," : "");
        }

        @Override
        public int read() throws IOException {
            return nextValue();
        }

        private int nextValue() {
            if (bufIndex < buf.length) {
                // more to read in the current buffer
                return buf[bufIndex++];
            }

            int value;
            bufIndex = 0;

            // need to get a new string and maybe change phase
            switch (phase) {
                case PRE:
                    phase = StreamGeneratorPhase.DYNAMIC;
                    buf = generateHost(index, max).getBytes(StandardCharsets.UTF_8);
                    index++;
                    value = nextValue();
                    break;
                case DYNAMIC:
                    if (index < max) {
                        buf = generateHost(index, max).getBytes(StandardCharsets.UTF_8);
                        index++;
                    } else {
                        phase = StreamGeneratorPhase.POST;
                        buf = post.getBytes(StandardCharsets.UTF_8);
                    }
                    value = nextValue();
                    break;
                case POST:
                default:
                    value = -1;
                    break;
            }

            return value;
        }
    }
}
