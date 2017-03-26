package com.walmartlabs.concord.server.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class Main {

    public static void main(String[] args) throws Exception {
        WireMockServer mock = new WireMockServer(options().port(8001));

        mockProcessHistory(mock);

        mock.start();
    }

    private static void mockProcessHistory(WireMockServer mock) {
        List<ProcessHistoryEntry> rows = Stream.generate(Main::randomHistoryEntry)
                .limit(10).collect(Collectors.toList());

        mock.stubFor(get(urlPathEqualTo("/api/v1/history"))
                .willReturn(aResponse()
                        .okForJson(rows)));

    }

    private static ProcessHistoryEntry randomHistoryEntry() {
        return new ProcessHistoryEntry(UUID.randomUUID().toString(),
                new Date(), "aUser", ProcessStatus.RUNNING, new Date(), "test.log");
    }
}
