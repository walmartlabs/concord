package com.walmartlabs.concord.plugins.boo;

import com.oneops.boo.BooCli;
import com.walmartlabs.concord.common.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Named
public class BooTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(BooTask.class);

    @Override
    public String getKey() {
        return "boo";
    }

    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Inside boo task impl. Input: " + args.get("input"));
        BooCli boo = new BooCli();
        //String booTemplatePath = payloadPath + "/" + args.get("booTemplateLocation");
        Path booTemplatePath = Paths.get(payloadPath).resolve(String.valueOf(args.get("booTemplateLocation")));
        if (!Files.exists(booTemplatePath)) {
            // we can't continue
            throw new IOException("Boo template file not found: " + booTemplatePath.toAbsolutePath());
        }

        Map<String, String> booTemplateVariables = (Map<String, String>) args.get("booTemplateVariables");
        log.info("boo Variables: " + booTemplateVariables);
        log.info("boo template path: " + booTemplatePath.toAbsolutePath());
        boo.init(booTemplatePath.toFile(), null, booTemplateVariables , null);
        boo.createOrUpdatePlatforms();
    }
}
