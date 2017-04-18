package com.walmartlabs.concord.plugins.boo;

import com.oneops.boo.BooCli;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Named("boo")
public class BooTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(BooTask.class);

    @SuppressWarnings("unchecked")
    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Inside boo task impl. Input: " + args.get("input"));

        Path booTemplatePath = Paths.get(payloadPath).resolve(String.valueOf(args.get("booTemplateLocation")));
        if (!Files.exists(booTemplatePath)) {
            // we can't continue
            throw new IOException("Boo template file not found: " + booTemplatePath.toAbsolutePath());
        }

        Map<String, String> booTemplateVariables = filterTemplateVariables(args);
        log.info("boo variables: " + booTemplateVariables);
        log.info("boo template path: " + booTemplatePath.toAbsolutePath());

        BooCli boo = new BooCli();
        boo.init(booTemplatePath.toFile(), null, booTemplateVariables , null);
        boo.createOrUpdatePlatforms();
    }

    private static Map<String, String> filterTemplateVariables(Map<String, Object> m) {
        Map<String, String> result = new HashMap<>(m.size());
        m.forEach((k, v) -> {
            result.put(k, v.toString());
        });
        return result;
    }
}
