package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Named
@Deprecated
public class RunPlaybookTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    @Override
    public String getKey() {
        return "ansible";
    }

    @SuppressWarnings("unchecked")
    public void run(Map<String, Object> args, String playbook, String playbookPath, String tags) throws Exception {
        Collection<String> hosts = (Collection<String>) args.get("hosts");
        String user = (String) args.get("user");
        String password = (String) args.get("password");
        String hostsFileTemplate = (String) args.get("hostsFileTemplate");
        Map<String, String> extraVars = (Map<String, String>) args.get("extraVars");
        boolean local = (boolean) args.getOrDefault("local", false);
        run(hosts, playbookPath, playbook, user, password, hostsFileTemplate, extraVars, tags, local);
    }

    public void run(Collection<String> hosts, String playbooksPath, String playbook,
                    String user, String password, String hostsFileTemplate, Map<String, String> extraVars,
                    String tags) throws Exception {

        run(hosts, playbooksPath, playbook, user, password, hostsFileTemplate, extraVars, tags, false);
    }

    public void run(Collection<String> hosts, String playbooksPath, String playbook,
                    String user, String password, String hostsFileTemplate, Map<String, String> extraVars,
                    String tags, boolean local) throws Exception {

        if (hosts == null || hosts.isEmpty()) {
            log.warn("execution -> no hosts were specified for this task, skipping");
            return;
        }

        String evs = formatExtraVars(extraVars);

        createAnsibleCfgFile(playbooksPath + "/ansible.cfg", local);

        // TODO prepare a script instead of passing everything as args
        String hostsDir = makeHostsDir(hostsFileTemplate, hosts, user, password);
        String[] cmd = formatCmd(hostsDir, playbook, user, evs, tags);
        log.debug("execution -> cmd: {}", String.join(" ", cmd));

        log.info("Running the playbook: {}/{}", playbooksPath, playbook);

        ProcessBuilder b = new ProcessBuilder()
                .command(cmd)
                .directory(new File(playbooksPath))
                .redirectErrorStream(true);

        // TODO b.environment().put("ANSIBLE_FORCE_COLOR", "true");

        Process p = b.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("ANSIBLE: {}", line);
        }

        int code = p.waitFor();
        if (code == SUCCESS_EXIT_CODE) {
            log.debug("execution -> done");
        } else {
            log.warn("Playbook is finished with code {}", code);
            log.debug("execution -> finished with exit code {}", code);
            throw new BpmnError("ansibleError", new IllegalStateException("Process finished with with exit code " + code));
        }
    }

    private static String formatExtraVars(Map<String, String> m) throws IOException {
        return new ObjectMapper().writeValueAsString(m);
    }

    private static String makeHostsDir(String template, Collection<String> hosts, String user, String passwd) throws IOException {
        StringBuilder hs = new StringBuilder();
        for (String h : hosts) {
            hs.append(h);

            if (user != null && passwd != null) {
                hs.append(" ansible_ssh_user=").append(user)
                        .append(" ansible_ssh_pass=").append(passwd)
                        .append(" ansible_sudo_pass=").append(passwd);
            }

            hs.append("\n");
        }

        String s = String.format(template.replaceAll("\\n", "\n"), hs.toString());

        Path tmpDir = Files.createTempDirectory("inventory");
        File f = new File(tmpDir.toFile(), "hosts");
        try (FileWriter w = new FileWriter(f)) {
            w.append(s);
            w.flush();
        }

        return tmpDir.toAbsolutePath().toString();
    }

    private static String[] formatCmd(String hostsPath, String playbook, String user, String extraVars, String tags) {
        List<String> l = new ArrayList<>(Arrays.asList(
                "ansible-playbook", "-T", "30", "-i", hostsPath, playbook, "-e", extraVars));

        if (user != null) {
            l.add("-u");
            l.add(user);
        }

        if (tags != null) {
            l.add("-t");
            l.add(tags);
        }

        return l.toArray(new String[l.size()]);
    }

    private static void createAnsibleCfgFile(String path, boolean local) throws IOException {
        String template = "[defaults]\n" +
                "transport = " + (local ? "local" : "smart") + "\n" +
                "host_key_checking = False\n" +
                "remote_tmp = /tmp/ansible/$USER\n" +
                "[ssh_connection]\n" +
                "control_path = %(directory)s/%%h-%%p-%%r\n" +
                "pipelining=true";

        try (OutputStream out = new FileOutputStream(path)) {
            out.write(template.getBytes());
        }

        log.debug("createAnsibleCfgFile ['{}'] -> done", path);
    }
}
