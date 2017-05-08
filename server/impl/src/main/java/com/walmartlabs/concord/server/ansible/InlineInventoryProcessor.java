package com.walmartlabs.concord.server.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Named
@Singleton
// TODO move into RunPlaybookTask2
public class InlineInventoryProcessor implements PayloadProcessor {

    private static final String INLINE_INVENTORY_KEY = "inventory";
    private static final String INLINE_INVENTORY_FILE = "_inlineInventory";
    private static final String INVENTORY_SCRIPT = "inventory.sh";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            return chain.process(payload);
        }

        Object o = req.get(INLINE_INVENTORY_KEY);
        if (o == null) {
            return chain.process(payload);
        }

        if (!(o instanceof Map)) {
            throw new ProcessException("Invalid inline inventory format. Expected a JSON object, got: " + o);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        // save the inline inventory data into an inventory file in the workspace
        Map<String, Object> inventory = (Map<String, Object>) o;
        saveInventory(workspace.resolve(INLINE_INVENTORY_FILE), inventory);

        // copy the dynamic inventory script to the workspace
        copyScript(workspace.resolve(AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME));

        return chain.process(payload);
    }

    private void saveInventory(Path dst, Map<String, Object> m) {
        try (OutputStream out = Files.newOutputStream(dst)) {
            objectMapper.writeValue(out, m);
        } catch (IOException e) {
            throw new ProcessException("Error while saving an inventory file", e);
        }
    }

    private void copyScript(Path dst) {
        try (InputStream in = InlineInventoryProcessor.class.getResourceAsStream(INVENTORY_SCRIPT)) {
            Files.copy(in, dst);
        } catch (IOException e) {
            throw new ProcessException("Error while copying an inventory script", e);
        }
    }
}
