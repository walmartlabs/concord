package com.walmartlabs.concord.plugins.boo;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.*;
import com.oneops.api.resource.model.Deployment;
import com.oneops.boo.BooCli;
import com.oneops.cms.simple.domain.CmsCISimple;
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.plugins.boo.oneops.Compute;
import com.walmartlabs.concord.plugins.boo.oneops.Fqdn;
import com.walmartlabs.concord.plugins.boo.oneops.Platform;
import io.takari.bpm.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Named("boo")
public class BooTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(BooTask.class);

    @SuppressWarnings("unchecked")
    public Deployment run(Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Inside boo task impl. Boo yaml : " + args.get("booTemplateLocation"));
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
        Deployment deployment = boo.createOrUpdatePlatforms();
        return deployment;
    }

    public void run(ExecutionContext ctx, Map<String, Object> args, String payloadPath) throws Exception {
        Deployment ooDeployment = run(args, payloadPath);
        com.walmartlabs.concord.plugins.boo.oneops.Deployment deployment = new com.walmartlabs.concord.plugins.boo.oneops.Deployment();
        deployment.setId(ooDeployment.getDeploymentId());
        deployment.setNsPath(ooDeployment.getNsPath());
        deployment.setStatus(ooDeployment.getDeploymentState());

        if (deployment != null && deployment.getNsPath() != null && ! deployment.getNsPath().isEmpty()) {
            ctx.setVariable("deployment", deployment);
            ctx.setVariable("host", args.get("host"));

            //query oneops to get all the CIs in this environment "namespace"
            String response = HttpRequest.get(args.get("oneopsApiHost") + "/adapter/rest/cm/simple/cis?nsPath="
                    + deployment.getNsPath() + "&recursive=true")
                    .accept("application/json").body();
            log.debug("OO response : " + response);

            Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new JsonDeserializer() {
                public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    return new Date(json.getAsJsonPrimitive().getAsLong());
                }
            }).create();

            CmsCISimple[] cis = gson.fromJson(response, CmsCISimple[].class);

            if (cis != null && cis.length > 0) {
                List<Platform> platforms = buildPlatforms(cis);
                ctx.setVariable("platforms", platforms);
                log.info("set these platforms to ctx: " + platforms);
            }
        } else {
            log.warn("No deployment returned by Boo !");
        }
    }

    private List<Platform> buildPlatforms(CmsCISimple[] cis) {
        List<Platform> platforms = new ArrayList<>();
        for (CmsCISimple ci : cis) {
            if (ci.getCiClassName().matches("bom.*\\.Fqdn")) {
                buildFqdn(ci, platforms);
            } else if (ci.getCiClassName().matches("bom.*\\.Compute")) {
                buildCompute(ci, platforms);
            }
        }
        return platforms;
    }

    private void buildFqdn(CmsCISimple ci, List<Platform> platforms) {
        String nsPath = ci.getNsPath();
        if (nsPath == null || ! nsPath.contains("/")) {
            log.warn("nspath invalid for ci id: " + ci.getCiId());
            return;
        }
        String[] nsTokens = nsPath.split("/");
        String platformName = nsTokens[nsTokens.length - 2];
        Platform platform = null;
        for (Platform p : platforms) {
            if (p.getName().equals(platformName)) {
                platform = p;
            }
        }
        if (platform == null) {
            platform = new Platform();
            platform.setName(platformName);
            platforms.add(platform);
        }

        Fqdn fqdn = new Fqdn();
        fqdn.setEntries(ci.getCiAttributes().get("entries").replace("\"", ""));
        fqdn.setAliases(ci.getCiAttributes().get("aliases").replace("\"", ""));
        fqdn.setFullAliases(ci.getCiAttributes().get("full_aliases").replace("\"", ""));

        platform.addFqdn(fqdn);
    }

    private void buildCompute(CmsCISimple ci, List<Platform> platforms) {
        String nsPath = ci.getNsPath();
        if (nsPath == null || ! nsPath.contains("/")) {
            log.warn("nspath invalid for ci id: " + ci.getCiId());
            return;
        }
        String[] nsTokens = nsPath.split("/");
        String platformName = nsTokens[nsTokens.length - 2];
        Platform platform = null;
        for (Platform p : platforms) {
            if (p.getName().equals(platformName)) {
                platform = p;
            }
        }
        if (platform == null) {
            platform = new Platform();
            platform.setName(platformName);
            platforms.add(platform);
        }

        Compute compute = new Compute();
        compute.setPrivateIp(ci.getCiAttributes().get("private_ip"));
        compute.setPublicIp(ci.getCiAttributes().get("public_ip"));

        platform.addCompute(compute);
    }

    private static Map<String, String> filterTemplateVariables(Map<String, Object> m) {
        Map<String, String> result = new HashMap<>(m.size());
        m.forEach((k, v) -> {
            result.put(k, v.toString());
        });
        return result;
    }
}
