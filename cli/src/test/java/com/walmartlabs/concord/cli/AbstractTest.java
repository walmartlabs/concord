package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractTest {
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    public void setUpStreams() {
        out.reset();
        err.reset();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    protected void assertLog(String pattern) {
        String outStr = out.toString();
        if (grep(outStr, pattern) != 1) {
            fail("Expected a single log entry: '" + pattern + "', got: \n" + outStr);
        }
    }

    protected void assertLog(String pattern, int times) {
        String outStr = out.toString();
        int found = grep(outStr, pattern);
        if (found != times) {
            fail("Expected [" + times + "] log entries: '" + pattern + "', got [" + found + "]: \n" + outStr);
        }
    }

    protected String stdOut() {
        return out.toString();
    }

    protected String stdErr() {
        return err.toString();
    }

    private static int grep(String str, String pattern) {
        int cnt = 0;

        String[] lines = str.split("\\r?\\n");
        for (String line : lines) {
            if (line.matches(pattern)) {
                cnt++;
            }
        }

        return cnt;
    }
}
