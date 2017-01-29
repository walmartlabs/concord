package com.walmartlabs.concord.common.format;

import io.takari.bpm.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Named
public class AutoParser implements MultipleDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(AutoParser.class);

    private final Collection<PriorityBasedParser> parsers;

    @Inject
    public AutoParser(Collection<PriorityBasedParser> parsers) {
        this.parsers = parsers;
    }

    @Override
    public Collection<ProcessDefinition> parse(InputStream in) throws ParserException {
        if (in == null) {
            throw new IllegalArgumentException("Input can't be null");
        }

        File tmp = null;
        try {
            try {
                tmp = makeTmpFile(in);
            } catch (IOException e) {
                throw new ParserException("Can't create a temporary file", e);
            }

            List<PriorityBasedParser> ps = new ArrayList<>(this.parsers);
            ps.sort(Comparator.comparingInt(PriorityBasedParser::getPriority));

            for (PriorityBasedParser p : ps) {
                try (InputStream tmpIn = new BufferedInputStream(new FileInputStream(tmp))) {
                    Collection<ProcessDefinition> pds = p.parse(tmpIn);
                    log.debug("parse -> parsed by '{}', got '{}'", p, pds.stream().map(ProcessDefinition::getId).toArray());
                    return pds;
                } catch (IOException e) {
                    throw new ParserException("parse -> error while trying to parse the data with '" + p + "'", e);
                } catch (ParserException e) {
                    log.debug("parse -> not parseable by '{}', skipping", p, e);
                }
            }
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }

        throw new ParserException("Can't find a suitable parser for the specified data");
    }

    private static File makeTmpFile(InputStream in) throws IOException {
        File f = File.createTempFile("auto", "stream");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            int read;
            byte[] ab = new byte[1024];

            while ((read = in.read(ab)) > 0) {
                out.write(ab, 0, read);
            }

            out.flush();
        }
        return f;
    }
}
