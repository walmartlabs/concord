package com.walmartlabs.concord.server.events;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GithubUtilsTest {

    @Test
    public void testParse() throws Exception {
        assertRepositoryName("git@gecgithub01.walmart.com:h1sammo/Anisble-GLS-AppInstall.git", "h1sammo/Anisble-GLS-AppInstall");
        assertRepositoryName("git+https://github.com/owner/name.git", "owner/name");
        assertRepositoryName("git://gh.pages.com/owner/name.git", "owner/name");
        assertRepositoryName("git://github.assemble.com/owner/name.git", "owner/name");
        assertRepositoryName("git://github.assemble.two.com/owner/name.git", "owner/name");
        assertRepositoryName("git://github.com/owner/name", "owner/name");
        assertRepositoryName("git@github.com/owner/name.git", "owner/name");
        assertRepositoryName("git@github.com:owner/name.git", "owner/name");
        assertRepositoryName("git@github.com:8080/owner/name.git", "owner/name");
        assertRepositoryName("github.com:owner/name.git", "owner/name");
        assertRepositoryName("github:owner/name", "owner/name");
        assertRepositoryName("http://github.com:8080/owner/name", "owner/name");
        assertRepositoryName("http://github.com/owner/name", "owner/name");
        assertRepositoryName("http://github.com/owner/name.git", "owner/name");
        assertRepositoryName("http://github.com/owner/name/tree", "owner/name");
        assertRepositoryName("http://github.com/owner/name/tree/master", "owner/name");
        assertRepositoryName("https://assemble@github.com/owner/name.git", "owner/name");
        assertRepositoryName("https://github.com/owner/name/blob/249b21a86400b38969cee3d5df6d2edf8813c137/README.md", "owner/name");
//        assertRepositoryName("git@github.com:owner/name.git#1.2.3", "owner/name");
    }

    private static void assertRepositoryName(String url, String expected) throws Exception {
        String actual = GithubUtils.getRepositoryName(url);
        assertEquals(expected, actual);
    }
}
