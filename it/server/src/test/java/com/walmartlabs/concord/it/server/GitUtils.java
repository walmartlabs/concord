package com.walmartlabs.concord.it.server;

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


import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

public final class GitUtils {

    public static Path createBareRepository(Path data) throws Exception {
        // init bare repository
        Path repo = createTempDir();
        Git.init().setBare(true).setDirectory(repo.toFile()).call();

        // clone the repository into a new directory
        Path workdir = createTempDir();
        Git git = Git.cloneRepository()
                .setDirectory(workdir.toFile())
                .setURI("file://" + repo.toString())
                .call();

        // copy our files into the repository
        IOUtils.copy(data, workdir);

        // add, commit, and push copied files
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial message").call();
        git.push().call();

        return repo;
    }

    protected static Path createTempDir() throws IOException {
        Path tmpDir = Files.createTempDirectory("test");
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return tmpDir;
    }

    private GitUtils() {
    }
}
