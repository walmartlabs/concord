package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.repository.*;
import com.walmartlabs.concord.server.api.security.secret.*;
import com.walmartlabs.concord.server.api.template.CreateTemplateResponse;
import com.walmartlabs.concord.server.api.template.DeleteTemplateResponse;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.api.template.UpdateTemplateResponse;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;
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
        CreateProjectResponse cpr = projectResource.create(new CreateProjectRequest(projectName, new String[]{templateName}));
        assertTrue(cpr.isOk());

        String id = cpr.getId();

        ProjectEntry e1 = projectResource.get(id);
        assertNotNull(e1);

        UpdateProjectResponse upr = projectResource.update(id, new UpdateProjectRequest(new String[0]));
        assertTrue(upr.isOk());

        List<ProjectEntry> l = projectResource.list(null, false);
        ProjectEntry e2 = findProject(l, id);
        assertNotNull(e2);
        assertEquals(0, e2.getTemplates().length);

        DeleteProjectResponse dpr = projectResource.delete(id);
        assertTrue(dpr.isOk());
    }

    @Test
    public void testRepository() throws Exception {
        String projectName = "project#" + System.currentTimeMillis();
        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.create(new CreateProjectRequest(projectName));

        String projectId = cpr.getId();

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        RepositoryResource repositoryResource = proxy(RepositoryResource.class);
        CreateRepositoryResponse crr = repositoryResource.create(new CreateRepositoryRequest(projectId, repoName, "n/a"));
        assertTrue(crr.isOk());

        String repoId = crr.getId();

        UpdateRepositoryResponse urr = repositoryResource.update(repoId, new UpdateRepositoryRequest("something"));
        assertTrue(urr.isOk());

        List<RepositoryEntry> l = repositoryResource.list(null, true);
        RepositoryEntry e = findRepository(l, repoId);
        assertNotNull(e);
        assertEquals("something", e.getUrl());

        DeleteRepositoryResponse drr = repositoryResource.delete(repoId);
        assertTrue(drr.isOk());
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

        PublicKeyResponse pkr2 = secretResource.getPublicKey(id);
        assertEquals(pkr.getPublicKey(), pkr2.getPublicKey());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, id);
        assertNotNull(s);
        assertEquals(keyName, s.getName());

        // ---

        secretResource.delete(id);

        // ---

        try {
            secretResource.getPublicKey(id);
            fail("should fail");
        } catch (BadRequestException e) {
        }
    }

    @Test
    public void testSecretUsernamePassword() throws Exception {
        String keyName = "key#" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        UploadSecretResponse usr = secretResource.addUsernamePassword(keyName, new UsernamePasswordRequest("something", new char[] { 'a', 'b', 'c'}));
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

    private static RepositoryEntry findRepository(List<RepositoryEntry> l, String id) {
        return l.stream().filter(e -> id.equals(e.getId())).findAny().get();
    }

    private static SecretEntry findSecret(List<SecretEntry> l, String id) {
        return l.stream().filter(e -> id.equals(e.getId())).findAny().get();
    }
}
