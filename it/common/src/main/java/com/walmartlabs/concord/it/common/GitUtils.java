package com.walmartlabs.concord.it.common;

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
import org.eclipse.jgit.transport.RefSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

public final class GitUtils {

    public static Path createBareRepository(Path data) throws Exception {
        return createBareRepository(data, (Path)null);
    }

    public static Path createBareRepository(Path data, Path baseTmpDir) throws Exception {
        return createBareRepository(data, "initial message", baseTmpDir);
    }

    public static Path createBareRepository(Path data, String commitMessage) throws Exception {
        return createBareRepository(data, commitMessage, null);
    }

    public static Path createBareRepository(Path data, String commitMessage, Path baseTmpDir) throws Exception {
        // init bare repository
        Path tmp = createTempDir(baseTmpDir);
        Path repo = tmp.resolve("test");
        Files.createDirectories(repo);

        Git.init().setBare(true).setDirectory(repo.toFile()).call();

        // clone the repository into a new directory
        Path workdir = createTempDir(baseTmpDir);
        Git git = Git.cloneRepository()
                .setDirectory(workdir.toFile())
                .setURI("file://" + repo.toString())
                .call();

        // copy our files into the repository
        IOUtils.copy(data, workdir);

        // add, commit, and push copied files
        git.add().addFilepattern(".").call();
        git.commit().setMessage(commitMessage).call();
        git.push().call();

        return repo;
    }

    public static void createNewBranch(Path bareRepo, String branch, Path src) throws Exception {
        createNewBranch(bareRepo, branch, src, null);
    }

    public static void createNewBranch(Path bareRepo, String branch, Path src, Path baseTmpDir) throws Exception {
        Path dir = createTempDir(baseTmpDir);

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .call();

        IOUtils.copy(src, dir, StandardCopyOption.REPLACE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage("adding files from " + src.getFileName())
                .call();

        git.push()
                .setRefSpecs(new RefSpec(branch + ":" + branch))
                .call();
    }

    protected static Path createTempDir(Path base) throws IOException {
        Path tmpDir ;
        if (base != null) {
            tmpDir = Files.createTempDirectory(base, "test");
        } else {
            tmpDir = Files.createTempDirectory("test");
        }
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return tmpDir;
    }

    private GitUtils() {
    }
}
