package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GitUtils {

    public static Path createBareRepository(Path data) throws Exception {
        // init bare repository
        Path repo = Files.createTempDirectory("test");
        Git.init().setBare(true).setDirectory(repo.toFile()).call();

        // clone the repository into a new directory
        Path workdir = Files.createTempDirectory("test");
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

    private GitUtils() {
    }
}
