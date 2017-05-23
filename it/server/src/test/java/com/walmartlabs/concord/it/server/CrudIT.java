package com.walmartlabs.concord.it.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingRequest;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingResponse;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import com.walmartlabs.concord.server.api.security.ldap.LdapResource;
import com.walmartlabs.concord.server.api.security.secret.*;
import com.walmartlabs.concord.server.api.template.CreateTemplateResponse;
import com.walmartlabs.concord.server.api.template.DeleteTemplateResponse;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import com.walmartlabs.concord.server.api.template.UpdateTemplateResponse;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import com.walmartlabs.concord.server.api.user.RoleResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.Assert.*;

public class CrudIT extends AbstractServerIT {

    @Test
    public void testTemplates() {
        TemplateResource templateResource = proxy(TemplateResource.class);

        String name = "template_" + System.currentTimeMillis();
        CreateTemplateResponse ctr = templateResource.create(name, new ByteArrayInputStream(new byte[]{0, 1, 2}));
        assertTrue(ctr.isOk());

        UpdateTemplateResponse utr = templateResource.update(name, new ByteArrayInputStream(new byte[]{0, 1}));
        assertTrue(utr.isOk());

        DeleteTemplateResponse dtr = templateResource.delete(name);
        assertTrue(dtr.isOk());
    }

    @Test
    public void testProject() {
        String templateName = "template_" + System.currentTimeMillis();
        TemplateResource templateResource = proxy(TemplateResource.class);
        templateResource.create(templateName, new ByteArrayInputStream(new byte[]{0, 1, 2}));

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "project_" + System.currentTimeMillis();
        CreateProjectResponse cpr = projectResource.createOrUpdate(new CreateProjectRequest(projectName, Collections.singleton(templateName), null));
        assertTrue(cpr.isOk());

        cpr = projectResource.createOrUpdate(new CreateProjectRequest(projectName, Collections.singleton(templateName), null));
        assertTrue(cpr.isOk());

        // ---

        ProjectEntry e1 = projectResource.get(projectName);
        assertNotNull(e1);

        UpdateProjectResponse upr = projectResource.update(projectName, new UpdateProjectRequest(null, null));
        assertTrue(upr.isOk());

        List<ProjectEntry> l = projectResource.list(null, false);
        ProjectEntry e2 = findProject(l, projectName);
        assertNotNull(e2);
        assertEquals(0, e2.getTemplates().size());

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProjectCfg() throws Exception {
        ObjectMapper om = new ObjectMapper();

        String json1 = "{ \"smtp\": { \"host\": \"localhost\", \"port\": 25 }, \"ssl\": true }";
        Map<String, Object> cfg = om.readValue(json1, Map.class);

        // ---

        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "project_" + System.currentTimeMillis();
        projectResource.createOrUpdate(new CreateProjectRequest(projectName, null, null, null, cfg));

        // ---

        Map<String, Object> m1 = projectResource.getConfiguration(projectName, "smtp");
        assertNotNull(m1);
        assertEquals(25, m1.get("port"));

        assertNull(projectResource.getConfiguration(projectName, "somethingElse"));

        // ---

        String json2 = "{ \"port\": 2525 }";
        Map<String, Object> partial = om.readValue(json2, Map.class);

        projectResource.updateConfiguration(projectName, "smtp", partial);

        // ---

        Map<String, Object> m2 = projectResource.getConfiguration(projectName, "smtp");
        assertNotNull(m2);
        assertEquals(2525, m2.get("port"));
    }

    @Test
    public void testRepository() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String branch = "branch_" + System.currentTimeMillis();
        String commitId = "commitId_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new CreateProjectRequest(projectName, null, Collections.singletonMap(repoName, new UpdateRepositoryRequest("n/a", branch, commitId, null))));

        // ---

        UpdateRepositoryResponse urr = projectResource.updateRepository(projectName, repoName, new UpdateRepositoryRequest("something", branch, commitId,null));
        assertTrue(urr.isOk());

        // ---

        List<RepositoryEntry> l = projectResource.listRepositories(projectName, null, true);
        RepositoryEntry e = findRepository(l, repoName);
        assertNotNull(e);
        assertEquals("something", e.getUrl());
        assertEquals(branch, e.getBranch());
        assertEquals(commitId, e.getCommitId());

        DeleteRepositoryResponse drr = projectResource.deleteRepository(projectName, repoName);
        assertTrue(drr.isOk());
    }

    @Test
    public void testNonUniqueRepositoryNames() throws Exception {
        String projectName1 = "project1_" + System.currentTimeMillis();
        String projectName2 = "project2_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new CreateProjectRequest(projectName1, null, null, null, null));
        projectResource.createOrUpdate(new CreateProjectRequest(projectName2, null, null, null, null));

        // ---

        String repoName = "repo_" + System.currentTimeMillis();
        CreateRepositoryResponse crr1 = projectResource.createRepository(projectName1, new CreateRepositoryRequest(repoName, "n/a", null, null, null));
        assertTrue(crr1.isOk());

        CreateRepositoryResponse crr2 = projectResource.createRepository(projectName2, new CreateRepositoryRequest(repoName, "n/a", null, null, null));
        assertTrue(crr2.isOk());
    }

    @Test
    public void testSecretKeyPair() throws Exception {
        String keyName = "key_" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        PublicKeyResponse pkr = secretResource.createKeyPair(keyName);
        assertTrue(pkr.isOk());
        assertNotNull(pkr.getPublicKey());

        // ---

        PublicKeyResponse pkr2 = secretResource.getPublicKey(keyName);
        assertEquals(pkr.getPublicKey(), pkr2.getPublicKey());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, keyName);
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
        String keyName = "key_" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        UploadSecretResponse usr = secretResource.addUsernamePassword(keyName, new UsernamePasswordRequest("something", new char[]{'a', 'b', 'c'}));
        assertTrue(usr.isOk());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, keyName);
        assertNotNull(s);
        assertEquals(SecretType.USERNAME_PASSWORD, s.getType());
        assertEquals(keyName, s.getName());
    }

    @Test
    public void testLdapMappings() throws Exception {
        String roleA = "roleA_" + System.currentTimeMillis();
        String roleB = "roleB_" + System.currentTimeMillis();
        String ldapDn = "testDn_" + System.currentTimeMillis();

        RoleResource roleResource = proxy(RoleResource.class);
        roleResource.createOrUpdate(new RoleEntry(roleA, "A", "1", "2"));
        roleResource.createOrUpdate(new RoleEntry(roleB, "B", "2", "3"));

        // ---

        LdapResource ldapResource = proxy(LdapResource.class);
        CreateLdapMappingResponse clmr = ldapResource.createOrUpdate(new CreateLdapMappingRequest(ldapDn, roleA, roleB));
        assertTrue(clmr.isCreated());

        List<LdapMappingEntry> l = ldapResource.listMappings();
        assertFalse(l.isEmpty());

        boolean found = false;
        for (LdapMappingEntry e : l) {
            if (e.getId().equals(clmr.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found);

        ldapResource.deleteMapping(clmr.getId());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static RepositoryEntry findRepository(List<RepositoryEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static SecretEntry findSecret(List<SecretEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }
}
