package com.walmartlabs.concord.server.ansible;

import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;

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
    public static final AttachmentKey DYNAMIC_INVENTORY_FILE = AttachmentKey.register("dynamicInventory");

    @Override
    public Payload process(Chain chain, Payload payload) {
        if (!copy(payload, INVENTORY_FILE, AnsibleConstants.INVENTORY_FILE_NAME)) {
            if (!copy(payload, DYNAMIC_INVENTORY_FILE, AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME)) {
                return chain.process(payload);
            }
        }

        payload = payload.removeAttachment(INVENTORY_FILE)
                .removeAttachment(DYNAMIC_INVENTORY_FILE);

        return chain.process(payload);
    }

    private static boolean copy(Payload payload, AttachmentKey src, String dstName) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        Path p = payload.getAttachment(src);
        if (p == null) {
            return false;
        }

        Path dst = workspace.resolve(dstName);
        try {
            Files.copy(p, dst);
        } catch (IOException e) {
            throw new ProcessException("Error while copying an inventory file: " + p, e);
        }

        return true;
    }
}
