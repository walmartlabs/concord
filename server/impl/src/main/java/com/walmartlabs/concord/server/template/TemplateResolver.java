package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;

@Named
public class TemplateResolver {

    private final TemplateDao templateDao;

    @Inject
    public TemplateResolver(TemplateDao templateDao) {
        this.templateDao = templateDao;
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
        return getPredefinedTemplateUrl(name) != null || templateDao.exists(name);
    }

    private Path resolvePredefined(String name) throws IOException {
        URL url = getPredefinedTemplateUrl(name);
        if (url == null) {
            return null;
        }

        try {
            URI uri = url.toURI();
            return makeCopy(Paths.get(uri));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
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

    private static URL getPredefinedTemplateUrl(String name) {
        return TemplateResolver.class.getResource(name);
    }
}
