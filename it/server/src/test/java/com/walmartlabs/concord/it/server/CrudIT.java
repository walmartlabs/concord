package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.secret.*;
import com.walmartlabs.concord.server.api.template.CreateTemplateResponse;
import com.walmartlabs.concord.server.api.template.DeleteTemplateResponse;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.api.template.UpdateTemplateResponse;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CrudIT extends AbstractServerIT {

    @Test
    public void testTemplates() {
        TemplateResource templateResource = proxy(TemplateResource.class);

        String name = "template#" + System.currentTimeMillis();
        CreateTemplateResponse ctr = templateResource.create(name, new ByteArrayInputStream(new byte[]{0, 1, 2}));
        assertTrue(ctr.isOk());

        String id = ctr.getId();

        UpdateTemplateResponse utr = templateResource.update(id, new ByteArrayInputStream(new byte[]{0, 1}));
        assertTrue(utr.isOk());

        DeleteTemplateResponse dtr = templateResource.delete(id);
        assertTrue(dtr.isOk());
    }

    @Test
    public void testProject() {
        String templateName = "template#" + System.currentTimeMillis();
        TemplateResource templateResource = proxy(TemplateResource.class);
        templateResource.create(templateName, new ByteArrayInputStream(new byte[]{0, 1, 2}));

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "project#" + System.currentTimeMillis();
        CreateProjectResponse cpr = projectResource.create(new CreateProjectRequest(projectName, Collections.singleton(templateName), null));
        assertTrue(cpr.isOk());

        ProjectEntry e1 = projectResource.get(projectName);
        assertNotNull(e1);

        UpdateProjectResponse upr = projectResource.update(projectName, new UpdateProjectRequest(null, null));
        assertTrue(upr.isOk());

        List<ProjectEntry> l = projectResource.list(null, false);
        ProjectEntry e2 = findProject(l, cpr.getId());
        assertNotNull(e2);
        assertEquals(0, e2.getTemplates().size());

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());
    }

    @Test
    public void testRepository() throws Exception {
        String projectName = "project#" + System.currentTimeMillis();
        String repoName = "repo#" + System.currentTimeMillis();
        String branch = "branch#" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.create(new CreateProjectRequest(projectName, null, Collections.singletonMap(repoName, new UpdateRepositoryRequest("n/a", branch, null))));

        // ---

        UpdateRepositoryResponse urr = projectResource.updateRepository(projectName, repoName, new UpdateRepositoryRequest("something", branch, null));
        assertTrue(urr.isOk());

        // ---

        List<RepositoryEntry> l = projectResource.listRepositories(projectName, null, true);
        RepositoryEntry e = findRepository(l, repoName);
        assertNotNull(e);
        assertEquals("something", e.getUrl());
        assertEquals(branch, e.getBranch());

        DeleteRepositoryResponse drr = projectResource.deleteRepository(projectName, repoName);
        assertTrue(drr.isOk());
    }

    @Test
    public void testNonUniqueRepositoryNames() throws Exception {
        String projectName1 = "project1#" + System.currentTimeMillis();
        String projectName2 = "project2#" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr1 = projectResource.create(new CreateProjectRequest(projectName1, null, null));
        CreateProjectResponse cpr2 = projectResource.create(new CreateProjectRequest(projectName2, null, null));

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        CreateRepositoryResponse crr1 = projectResource.createRepository(projectName1, new CreateRepositoryRequest(repoName, "n/a", null, null));
        assertTrue(crr1.isOk());

        CreateRepositoryResponse crr2 = projectResource.createRepository(projectName2, new CreateRepositoryRequest(repoName, "n/a", null, null));
        assertTrue(crr2.isOk());
    }

    @Test
    public void testSecretKeyPair() throws Exception {
        String keyName = "key#" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        PublicKeyResponse pkr = secretResource.createKeyPair(keyName);
        assertTrue(pkr.isOk());
        assertNotNull(pkr.getPublicKey());

        String id = pkr.getId();

        // ---

        PublicKeyResponse pkr2 = secretResource.getPublicKey(keyName);
        assertEquals(pkr.getPublicKey(), pkr2.getPublicKey());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, id);
        assertNotNull(s);
        assertEquals(keyName, s.getName());

        // ---

        secretResource.delete(keyName);

        // ---

        try {
            secretResource.getPublicKey(keyName);
            fail("should fail");
        } catch (BadRequestException e) {
        }
    }

    @Test
    public void testSecretUsernamePassword() throws Exception {
        String keyName = "key#" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        UploadSecretResponse usr = secretResource.addUsernamePassword(keyName, new UsernamePasswordRequest("something", new char[]{'a', 'b', 'c'}));
        assertTrue(usr.isOk());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, usr.getId());
        assertNotNull(s);
        assertEquals(SecretType.USERNAME_PASSWORD, s.getType());
        assertEquals(keyName, s.getName());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String id) {
        return l.stream().filter(e -> id.equals(e.getId())).findAny().get();
    }

    private static RepositoryEntry findRepository(List<RepositoryEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static SecretEntry findSecret(List<SecretEntry> l, String id) {
        return l.stream().filter(e -> id.equals(e.getId())).findAny().get();
    }
}
