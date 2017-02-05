package com.walmartlabs.concord.server.inventory;

import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.process.ProcessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Support for external Ansible inventories.
 * <p>
 * This processor takes an inventory file from a request and stores it in a request's workspace
 * for {@link com.walmartlabs.concord.plugins.ansible.RunPlaybookTask2} to pick it up later.
 */
public class InventoryProcessor implements PayloadProcessor {

    public static final AttachmentKey INVENTORY_FILE = AttachmentKey.register("inventory");

    @Override
    public Payload process(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        Path p = payload.getAttachment(INVENTORY_FILE);
        if (p == null) {
            return payload;
        }

        Path dst = workspace.resolve(AnsibleConstants.GENERATED_INVENTORY_FILE_NAME);
        try {
            Files.copy(p, dst);
        } catch (IOException e) {
            throw new ProcessException("Error while copying an inventory file: " + p, e);
        }

        return payload.removeAttachment(INVENTORY_FILE);
    }
}
