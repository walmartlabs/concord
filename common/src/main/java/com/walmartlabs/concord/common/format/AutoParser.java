package com.walmartlabs.concord.common.format;

import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Named
public class AutoParser implements WorkflowDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(AutoParser.class);

    private final Collection<PriorityBasedParser> parsers;

    @Inject
    public AutoParser(Collection<PriorityBasedParser> parsers) {
        this.parsers = parsers;
    }

    public AutoParser(PriorityBasedParser... parsers) {
        this(Arrays.asList(parsers));
    }

    @Override
    public WorkflowDefinition parse(String source, InputStream in) throws ParserException {
        if (in == null) {
            throw new IllegalArgumentException("Input can't be null");
        }

        Throwable lastError = null;

        Path tmp = null;
        try {
            try {
                tmp = makeTmpFile(in);
            } catch (IOException e) {
                throw new ParserException("Can't create a temporary file", e);
            }

            List<PriorityBasedParser> ps = new ArrayList<>(this.parsers);
            ps.sort(Comparator.comparingInt(PriorityBasedParser::getPriority));

            for (PriorityBasedParser p : ps) {
                try (InputStream tmpIn = Files.newInputStream(tmp)) {
                    return p.parse(source, tmpIn);
                } catch (IOException e) {
                    lastError = e;
                    throw new ParserException("parse -> error while trying to parse the data with '" + p + "'", e);
                } catch (ParserException e) {
                    lastError = e;
                    log.debug("parse -> not parseable by '{}', skipping", p, e);
                }
            }
        } finally {
            if (tmp != null) {
                try {
                    Files.delete(tmp);
                } catch (IOException e) {
                    log.warn("parse -> error removing a temporary file", e);
                }
            }
        }

        throw new ParserException("Can't find a suitable parser for the specified data", lastError);
    }

    private static Path makeTmpFile(InputStream in) throws IOException {
        Path p = Files.createTempFile("auto", ".stream");
        try (OutputStream out = Files.newOutputStream(p)) {
            IOUtils.copy(in, out);
        }
        return p;
    }
}
