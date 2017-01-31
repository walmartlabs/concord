package com.walmartlabs.concord.plugins.jenkins;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.takari.bpm.api.ExecutionContext;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TriggerJobTaskTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(options().dynamicPort());

    @Test
    public void test() throws Exception {
        String jobName = "my_job";
        String jenkinsUrl = "http://localhost:" + wireMock.port() + "/job/" + jobName + "/buildWithParameters";
        String abc = "abc#" + System.currentTimeMillis();

        Map<String, Object> args = new HashMap<>();
        args.put(TriggerJobTask.JENKINS_URL_KEY, jenkinsUrl);
        args.put("abc", abc);

        // ---

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.getVariables()).thenReturn(args);
        when(ctx.getVariable(anyString())).then(i -> args.get(i.getArguments()[0]));

        TriggerJobTask t = new TriggerJobTask();
        t.execute(ctx);

        // ---

        verify(postRequestedFor(urlPathMatching("/job/my_job/buildWithParameters"))
                .withQueryParam("abc", equalTo(abc)));
    }




}
