package com.walmartlabs.concord.it.common;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

public final class ITUtils {

    public static byte[] archive(URI uri) throws IOException {
        return archive(uri, null);
    }

    public static byte[] archive(URI uri, String depsDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            IOUtils.zip(zip, Paths.get(uri));
            if (depsDir != null) {
                IOUtils.zip(zip, InternalConstants.Files.LIBRARIES_DIR_NAME + "/", Paths.get(depsDir));
            }
        }
        return out.toByteArray();
    }

    private ITUtils() {
    }
}
