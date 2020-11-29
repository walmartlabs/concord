package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class GitUtils {

    /**
     * Creates a new bare Git repository using data from the provided
     * path.
     */
    public static Path createBareRepository(Path data) throws Exception {
        // init bare repository
        Path tmp = Files.createTempDirectory("git-client-test");
        Path repo = tmp.resolve("test");
        Files.createDirectories(repo);

        Git.init().setBare(true).setDirectory(repo.toFile()).call();

        // clone the repository into a new directory
        Path workdir = Files.createTempDirectory("workDir");
        Git git = Git.cloneRepository()
                .setDirectory(workdir.toFile())
                .setURI("file://" + repo.toString())
                .call();

        // copy our files into the repository
        IOUtils.copy(data, workdir);

        // add, commit, and push copied files
        git.add().addFilepattern(".").call();
        git.commit().setMessage("init from: " + data).call();
        git.push().call();

        return repo;
    }

    /**
     * Creates a new branch in the specified bare Git repository and
     * adds all files from the {@code src} directory.
     */
    public static RevCommit createNewBranch(Path bareRepo, String branch, Path src) throws Exception {
        Path dir = Files.createTempDirectory("repo-tmp");

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

        RevCommit commit = git.commit()
                .setMessage("adding files from " + src.getFileName())
                .call();

        git.push()
                .setRefSpecs(new RefSpec(branch + ":" + branch))
                .call();

        return commit;
    }

    /**
     * Adds all files from the {@code src} directory, commits then to
     * a bare Git repository and creates a new tag.
     */
    public static RevCommit createNewTag(Path bareRepo, String tag, Path src) throws Exception {
        Path dir = Files.createTempDirectory("repo-tmp");

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        IOUtils.copy(src, dir, StandardCopyOption.REPLACE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        RevCommit commit = git.commit()
                .setMessage("adding files from " + src.getFileName())
                .call();

        git.tag().setMessage("tagging").setName(tag).call();

        git.push()
                .setPushTags()
                .call();

        return commit;
    }

    private GitUtils() {
    }
}
