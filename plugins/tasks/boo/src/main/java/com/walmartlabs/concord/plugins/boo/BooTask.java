package com.walmartlabs.concord.plugins.boo;

import com.walmartlabs.concord.common.Task;
import com.wm.bfd.oo.BooCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.util.Map;

@Named
public class BooTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(BooTask.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    @Override
    public String getKey() {
        return "boo";
    }

    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Inside boo task impl. Input: " + args.get("input"));
        BooCli boo = new BooCli();
        String booTemplatePath = payloadPath + "/" + args.get("booTemplateLocation");
        log.info("boo template path: " + booTemplatePath);
        boo.init(new File(booTemplatePath), null, null);
        boo.createPacks(false, false);
    }
}
