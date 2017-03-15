package com.walmartlabs.concord.plugins.nexus.perf2;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.pool.AgentPool;
import com.walmartlabs.concord.common.Constants;
import io.takari.bpm.api.ExecutionContext;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.mockito.Mockito.*;

public class PerfTaskTest {

    private static final String SCENARIOS_DIR = "scenarios";

    @Test
    public void test() throws Exception {
        Path payloadDir = preparePayload();

        Collection<Map<String, Object>> scenarios = new ArrayList<>();
        scenarios.add(ImmutableMap.of(
                "name", "maven01",
                "agents", 1,
                "test.duration", "1 MINUTES"
        ));

        AgentResource agentResource = prepareJobResource();

        AgentPool agentPool = mock(AgentPool.class);
        when(agentPool.acquire(anyLong())).thenReturn(agentResource);

        AgentPoolFactory agentPoolFactory = mock(AgentPoolFactory.class);
        when(agentPoolFactory.create(anyCollection())).thenReturn(agentPool);

        // ---

        ExecutionContext ctx = new MockExecutionContext();
        ctx.setVariable(Constants.LOCAL_PATH_KEY, payloadDir.toAbsolutePath().toString());

        // ---

        PerfTask t = new PerfTask(agentPoolFactory);
        t.loadAndStart(ctx, "localhost", SCENARIOS_DIR, scenarios);
        t.waitToFinish(ctx, 10000);
        t.close(ctx);

        // ---

        verify(agentResource, times(1)).start(anyString(), eq(JobType.JUNIT_GROOVY), eq("maven01/scenario.groovy"), any(InputStream.class));
    }

    private static Path preparePayload() throws IOException {
        Path tmpDir = Files.createTempDirectory("junit");

        File libsDir = tmpDir.resolve(Constants.LIBRARIES_DIR_NAME).toFile();
        libsDir.mkdirs();

        File aLib = new File(libsDir, "a.jar");
        try (OutputStream out = new FileOutputStream(aLib)) {
            out.write(123);
        }

        File scenariosDir = new File(tmpDir.toFile(), SCENARIOS_DIR);
        File maven01Dir = new File(scenariosDir, "maven01");
        maven01Dir.mkdirs();

        File aScenario = new File(maven01Dir, "a.groovy");
        try (OutputStream out = new FileOutputStream(aScenario)) {
            out.write(123);
        }

        return tmpDir;
    }

    private static AgentResource prepareJobResource() throws Exception {
        AgentResource r = mock(AgentResource.class);
        when(r.getStatus(any(String.class)))
                .thenReturn(JobStatus.RUNNING)
                .thenReturn(JobStatus.COMPLETED);
        return r;
    }
}
