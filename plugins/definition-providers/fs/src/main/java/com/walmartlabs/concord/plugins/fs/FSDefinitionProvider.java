package com.walmartlabs.concord.plugins.fs;

import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.WorkflowDefinition;
import com.walmartlabs.concord.common.format.WorkflowDefinitionParser;
import com.walmartlabs.concord.common.format.WorkflowDefinitionProvider;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * Filesystem based definition provider. It will scan, parse and cache all process definition files in the
 * specified directory.
 * <p>
 * <i>Limitations:</i> entry point names and form IDs must be unique.
 */
public class FSDefinitionProvider implements WorkflowDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(FSDefinitionProvider.class);

    private final Map<String, ProcessDefinition> processes;
    private final Map<String, FormDefinition> forms;

    public FSDefinitionProvider(WorkflowDefinitionParser parser, Map<String, String> attributes,
                                Path baseDir, String... fileTypes) throws ExecutionException {

        Map<String, ProcessDefinition> mp = new HashMap<>();
        Map<String, FormDefinition> mf = new HashMap<>();

        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // skip "hidden" directories
                    String n = dir.getFileName().toString();
                    if (n.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // skip "hidden" files
                    String n = file.getFileName().toString();
                    if (n.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (matches(n, fileTypes)) {
                        parseAndStore(parser, attributes, file, mp, mf);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ExecutionException("Error scanning the path '" + baseDir + "'", e);
        }

        this.processes = mp;
        this.forms = mf;
    }

    @Override
    public ProcessDefinition getProcess(String id) {
        return processes.get(id);
    }

    @Override
    public FormDefinition getForm(String id) {
        return forms.get(id);
    }

    private static boolean matches(String n, String[] fileTypes) {
        for (String t : fileTypes) {
            if (n.matches(t)) {
                return true;
            }
        }

        return false;
    }

    private static void parseAndStore(WorkflowDefinitionParser parser, Map<String, String> attrs, Path f,
                                      Map<String, ProcessDefinition> processes,
                                      Map<String, FormDefinition> forms) throws IOException {

        try (InputStream in = Files.newInputStream(f)) {
            WorkflowDefinition wd = parser.parse(f.getFileName().toString(), in);

            Map<String, ProcessDefinition> pds = wd.getProcesses();
            if (pds != null) {
                for (Map.Entry<String, ProcessDefinition> e : pds.entrySet()) {
                    String id = e.getKey();
                    ProcessDefinition pd = e.getValue();
                    processes.put(id, mergeAttrs(pd, attrs));
                    log.info("parseAndStore ['{}'] -> got process '{}'", f, id);
                }
            }

            forms.putAll(wd.getForms());
        } catch (ParserException e) {
            log.warn("parseAndStore ['{}'] -> skipping, {}", f, e.getMessage());
        }
    }

    private static ProcessDefinition mergeAttrs(ProcessDefinition source, Map<String, String> attrs) {
        Map<String, String> m = new HashMap<>(source.getAttributes());
        m.putAll(attrs);
        return new ProcessDefinition(source, m);
    }
}
