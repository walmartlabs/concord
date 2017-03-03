package com.walmartlabs.concord.server.template;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;

@Named
@Singleton
public class TemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(TemplateResolver.class);
    private static final String TEMPLATE_DESCRIPTOR_PATH = "META-INF/concord/template.properties";
    private static final String TEMPLATE_BASE_RELATIVE_PATH = "../../..";

    private final TemplateDao templateDao;
    private final Map<String, Path> predefinedTemplates;

    @Inject
    public TemplateResolver(TemplateDao templateDao) {
        this.templateDao = templateDao;
        this.predefinedTemplates = load();
    }

    public Path get(String name) throws IOException {
        // check the predefined templates first
        Path dst = resolvePredefined(name);

        // check the db, if there is no predefined templates
        // with the specified name
        if (dst == null) {
            dst = resolveDb(name);
        }

        // no templates found, nothing to do
        if (dst == null) {
            return null;
        }

        return dst;
    }

    public boolean exists(String name) {
        return predefinedTemplates.containsKey(name) || templateDao.exists(name);
    }

    private Path resolvePredefined(String name) throws IOException {
        Path p = predefinedTemplates.get(name);
        if (p == null) {
            return null;
        }
        return makeCopy(p);
    }

    private Path resolveDb(String name) throws IOException {
        try (InputStream in = templateDao.getData(name)) {
            if (in == null) {
                return null;
            }

            try (ZipInputStream zip = new ZipInputStream(in)) {
                Path dst = Files.createTempDirectory("template");
                IOUtils.unzip(zip, dst);
                return dst;
            }
        }
    }

    private Path makeCopy(Path src) throws IOException {
        Path dst = Files.createTempDirectory("template");
        IOUtils.copy(src, dst);
        return dst;
    }

    private static Map<String, Path> load() {
        try {
            Map<String, Path> m = new HashMap<>();

            Enumeration<URL> urls = ClassLoader.getSystemResources(TEMPLATE_DESCRIPTOR_PATH);
            while (urls.hasMoreElements()) {
                URL u = urls.nextElement();

                String proto = u.getProtocol();
                if ("file".equals(proto)) {
                    Path desc = Paths.get(u.getPath());
                    String name = getTemplateName(desc);
                    Path baseDir = desc.resolve(TEMPLATE_BASE_RELATIVE_PATH).normalize();
                    m.put(name, baseDir);
                } else if ("jar".equals(proto)) {
                    Path jarFile = getJarPath(u.getPath());
                    Path baseDir = Files.createTempDirectory("template");
                    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jarFile))) {
                        IOUtils.unzip(zip, baseDir);
                    }

                    Path desc = baseDir.resolve(TEMPLATE_DESCRIPTOR_PATH);
                    if (!Files.exists(desc)) {
                        throw new IllegalArgumentException("File not found: " + desc);
                    }

                    String name = getTemplateName(desc);
                    m.put(name, baseDir);
                } else {
                    throw new IllegalArgumentException("Unknown protocol: " + proto);
                }
            }

            log.info("load -> found {} template(s): {}", m.size(), m.keySet());
            return m;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static String getTemplateName(Path desc) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(desc)) {
            props.load(in);
        }
        return props.getProperty("name");
    }

    private static Path getJarPath(String p) {
        int i = p.indexOf("file:");
        int j = p.indexOf("!", i);
        if (i >= 0 && j >= 0) {
            return Paths.get(p.substring(i + 5, j));
        } else {
            throw new IllegalArgumentException("Invalid descriptor location: " + p);
        }
    }
}
