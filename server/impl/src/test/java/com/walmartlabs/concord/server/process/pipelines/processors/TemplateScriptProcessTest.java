package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.TemplatesConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateScriptProcessTest {

    @TempDir
    Path workspace;

    private ProcessLogManager logManager;
    private TemplatesConfiguration cfg;

    private Chain chain;
    private Payload payload;

    @BeforeEach
    void init() {
        logManager = mock(ProcessLogManager.class);
        cfg = mock(TemplatesConfiguration.class);
        chain = mock(Chain.class);
        payload = mock(Payload.class);
    }

    @Test
    void testDisableScripting() {
        when(cfg.isAllowScripting()).thenReturn(false);
        var processor = new TemplateScriptProcessor(logManager, cfg);

        processor.process(chain, payload);

        verify(payload, times(0)).getHeader(any(), any());
    }

    @Test
    void testEnableScripting() {
        when(cfg.isAllowScripting()).thenReturn(true);
        var headerMatcher = ArgumentMatchers.<HeaderKey<Path>>argThat(header ->
                header.equals(Payload.WORKSPACE_DIR));
        when(payload.getHeader(headerMatcher)).thenReturn(workspace);

        var processor = new TemplateScriptProcessor(logManager, cfg);
        processor.process(chain, payload);

        verify(payload, times(1)).getHeader(Payload.WORKSPACE_DIR);
    }

}
