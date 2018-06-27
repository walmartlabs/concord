package com.walmartlabs.concord.server.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Support for external Ansible inventories.
 * <p>
 * This processor takes an inventory file from a request and stores it in a request's workspace
 * for {@code com.walmartlabs.concord.plugins.ansible.RunPlaybookTask2} to pick it up later.
 */
@Deprecated
public class InventoryProcessor implements PayloadProcessor {

    public static final AttachmentKey INVENTORY_FILE = AttachmentKey.register("inventory");
    public static final AttachmentKey DYNAMIC_INVENTORY_FILE = AttachmentKey.register("dynamicInventory");

    private static final String INVENTORY_FILE_NAME = "_inventory";
    private static final String DYNAMIC_INVENTORY_FILE_NAME = "_dynamicInventory";

    private final LogManager logManager;

    @Inject
    public InventoryProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        if (!copy(payload, INVENTORY_FILE, INVENTORY_FILE_NAME)) {
            if (!copy(payload, DYNAMIC_INVENTORY_FILE, DYNAMIC_INVENTORY_FILE_NAME)) {
                return chain.process(payload);
            }
        }

        deprecationWarning(payload.getInstanceId());

        payload = payload.removeAttachment(INVENTORY_FILE)
                .removeAttachment(DYNAMIC_INVENTORY_FILE);

        return chain.process(payload);
    }

    private boolean copy(Payload payload, AttachmentKey src, String dstName) {
        UUID instanceId = payload.getInstanceId();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        Path p = payload.getAttachment(src);
        if (p == null) {
            return false;
        }

        Path dst = workspace.resolve(dstName);
        try {
            Files.copy(p, dst);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while copying an inventory file: " + p, e);
            throw new ProcessException(instanceId, "Error while copying an inventory file: " + p, e);
        }

        return true;
    }

    private void deprecationWarning(UUID instanceId) {
        String msg = ".. WARNING ............................................................................\n" +
                " 'inventory' and 'dynamicInventory' request parameters are deprecated.\n" +
                " Please use 'inventoryFile' and 'dynamicInventoryFile' parameters of the Ansible task.\n" +
                ".......................................................................................\n";
        logManager.log(instanceId, msg);
    }
}
