package com.walmartlabs.concord.server.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.plugins.ansible.inventory.InventoryDao;
import com.walmartlabs.concord.plugins.ansible.inventory.InventoryException;
import com.walmartlabs.concord.plugins.ansible.inventory.InventoryProcessor;
import com.walmartlabs.concord.plugins.ansible.inventory.InventoryProcessor.InventoryType;
import com.walmartlabs.concord.server.process.Payload;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Named
public class InventoryPayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(InventoryPayloadProcessor.class);

    // TODO implement payload processor directly in the extension
    private final InventoryProcessor processor;

    @Inject
    public InventoryPayloadProcessor(InventoryDao dao) {
        this.processor = new InventoryProcessor(dao);
    }

    public void process(Payload payload) {
        Map<String, Object> meta = readMeta(payload.getData());

        // TODO constants
        String inventory = (String) meta.get("inventoryFile");
        InventoryType type = InventoryType.LOCAL;
        if (inventory == null) {
            inventory = (String) meta.get("inventory");
            type = InventoryType.STORED;
        }

        if (inventory == null) {
            return;
        }

        log.info("process ['{}'] -> using inventory ({}): {}", payload.getInstanceId(), type, inventory);

        Path data = payload.getData();

        Subject subject = SecurityUtils.getSubject();
        if (!processor.isUseAllowed(subject, inventory)) {
            throw new WebApplicationException("The current user does not have permissions to use this inventory file", Status.FORBIDDEN);
        }

        try {
            processor.process(data, type, inventory);
        } catch (InventoryException e) {
            throw new WebApplicationException("Error while processing an inventory file", e);
        }
    }

    private static Map<String, Object> readMeta(Path path) {
        Path p = path.resolve(Constants.METADATA_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptyMap();
        }

        ObjectMapper om = new ObjectMapper();
        try {
            return om.readValue(p.toFile(), Map.class);
        } catch (IOException e) {
            throw new WebApplicationException(e);
        }
    }
}
