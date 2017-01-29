package com.walmartlabs.concord.it.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.walmartlabs.concord.plugins.ansible.inventory.api.InventoryResource;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.template.TemplateResource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Ignore
public class AnsibleGitProjectIT extends ProjectIT {

    private Map<String, Object> args;

    @Before
    public void setUp() throws Exception {
        ObjectMapper om = new ObjectMapper();
        args = om.readValue(AnsibleGitProjectIT.class.getResourceAsStream("ansiblegitproject/request.json"), Map.class);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        String inventoryName = "myInventory#" + System.currentTimeMillis();
        InventoryResource inventoryResource = proxy(InventoryResource.class);
        try (InputStream in = AnsibleGitProjectIT.class.getResourceAsStream("ansiblegitproject/inventory.ini")) {
            inventoryResource.create(inventoryName, in);
        }
        // TODO constants
        args.put("inventory", inventoryName);

        // ---

        String templateName = "ansible#" + System.currentTimeMillis();
        TemplateResource templateResource = proxy(TemplateResource.class);
        try (InputStream in = new FileInputStream(ITConstants.TEMPLATES_DIR + "/ansible-template.zip")) {
            templateResource.create(templateName, in);
        }

        // ---

        String gitUrl = "git@gecgithub01.walmart.com:devtools/concord-ansible-example.git";

        // ---

        String projectName = "myProject#" + System.currentTimeMillis();
        String[] projectTemplates = {templateName};
        String userName = "myUser#" + System.currentTimeMillis();
        Set<String> permissions = Sets.newHashSet(
                String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName),
                Permissions.REPOSITORY_CREATE_NEW,
                String.format(Permissions.PROCESS_START_PROJECT, projectName),
                String.format(com.walmartlabs.concord.plugins.ansible.inventory.api.Permissions.INVENTORY_USE_INSTANCE, inventoryName));
        String repoName = "myRepo#" + System.currentTimeMillis();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";

        // ---

        ProcessStatusResponse psr = doTest(projectName, projectTemplates, userName, permissions, repoName, repoUrl, entryPoint, args);

        byte[] ab = getLog(psr);
        assertLog(".*hey!.*", ab);
    }
}
