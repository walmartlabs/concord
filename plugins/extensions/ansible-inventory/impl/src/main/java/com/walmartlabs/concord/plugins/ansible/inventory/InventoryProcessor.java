package com.walmartlabs.concord.plugins.ansible.inventory;

import com.walmartlabs.concord.plugins.ansible.inventory.api.AnsibleInventoryConstants;
import com.walmartlabs.concord.plugins.ansible.inventory.api.Permissions;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class InventoryProcessor {

    private static final Logger log = LoggerFactory.getLogger(InventoryProcessor.class);

    private final InventoryDao inventoryDao;

    public InventoryProcessor(InventoryDao inventoryDao) {
        this.inventoryDao = inventoryDao;
    }

    public boolean isUseAllowed(Subject subject, String inventoryName) {
        return subject.isPermitted(String.format(Permissions.INVENTORY_USE_INSTANCE, inventoryName));
    }

    public void process(Path payload, InventoryType type, String inventory) throws InventoryException {
        if (type == InventoryType.LOCAL) {
            processLocalInventory(payload, inventory);
        } else if (type == InventoryType.STORED) {
            processStoredInventory(payload, inventory);
        } else {
            throw new InventoryException("Unknown inventory type: " + type);
        }
    }

    private void processLocalInventory(Path payload, String inventory) throws InventoryException {
        Path src = payload.resolve(inventory);
        if (!Files.exists(src)) {
            throw new InventoryException("Inventory file not found: " + src.toAbsolutePath());
        }

        Path dst = payload.resolve(AnsibleInventoryConstants.GENERATED_INVENTORY_FILE_NAME);
        try {
            Files.copy(src, dst);
        } catch (IOException e) {
            throw new InventoryException("Error while writing an inventory file", e);
        }

        log.info("process ['{}', '{}'] -> copied an inventory file: {}", payload, inventory, src);
    }

    private void processStoredInventory(Path payload, String inventory) throws InventoryException {
        String id = inventoryDao.getId(inventory);
        InputStream in = id != null ? inventoryDao.getData(id) : null;
        if (in == null) {
            throw new InventoryException("Inventory file '" + inventory + "' not found");
        }

        Path p = payload.resolve(AnsibleInventoryConstants.GENERATED_INVENTORY_FILE_NAME);
        try {
            Files.copy(in, p);
        } catch (IOException e) {
            throw new InventoryException("Error while writing an inventory file", e);
        }

        log.info("process ['{}', '{}'] -> created an inventory file: {}", payload, inventory, p);
    }

    public enum InventoryType {
        LOCAL,
        STORED
    }
}
