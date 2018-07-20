package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class MultipleProjectFilesIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        Path template = zip(Paths.get(MultipleProjectFilesIT.class.getResource("multiProjectTemplate/template").toURI()));
        String templateUrl = "file://" + template.toAbsolutePath();

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("multiProjectTemplate/user").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("template", templateUrl);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, Concord!.*", ab);
    }

    private static Path zip(Path src) throws IOException {
        Path dst = IOUtils.createTempFile("template", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(dst))) {
            IOUtils.zip(zip, src);
        }
        return dst;
    }
}
