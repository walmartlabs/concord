package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Posix {

    public static final int DEFAULT_UNIX_MODE = 0644;

    public static int unixMode(Set<PosixFilePermission> s) {
        if (s == null || s.isEmpty()) {
            return DEFAULT_UNIX_MODE;
        }

        int i = 0;
        for (PosixFilePermission p : s) {
            switch (p) {
                case OWNER_EXECUTE:
                    i += 0100;
                    break;
                case OWNER_WRITE:
                    i += 0200;
                    break;
                case OWNER_READ:
                    i += 0400;
                    break;
                case GROUP_EXECUTE:
                    i += 0010;
                    break;
                case GROUP_WRITE:
                    i += 0020;
                    break;
                case GROUP_READ:
                    i += 0040;
                    break;
                case OTHERS_EXECUTE:
                    i += 0001;
                    break;
                case OTHERS_WRITE:
                    i += 0002;
                    break;
                case OTHERS_READ:
                    i += 0004;
                    break;
            }
        }
        return i;
    }

    public static Set<PosixFilePermission> posix(int unixMode) {
        if (unixMode <= 0) {
            return Collections.emptySet();
        }

        Set<PosixFilePermission> s = new HashSet<>();

        if ((unixMode & 0100) == 0100) {
            s.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((unixMode & 0200) == 0200) {
            s.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((unixMode & 0400) == 0400) {
            s.add(PosixFilePermission.OWNER_READ);
        }
        if ((unixMode & 0010) == 0010) {
            s.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((unixMode & 0020) == 0020) {
            s.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((unixMode & 0040) == 0040) {
            s.add(PosixFilePermission.GROUP_READ);
        }
        if ((unixMode & 0001) == 0001) {
            s.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        if ((unixMode & 0002) == 0002) {
            s.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((unixMode & 0004) == 0004) {
            s.add(PosixFilePermission.OTHERS_READ);
        }

        return s;
    }

    private Posix() {
    }
}
