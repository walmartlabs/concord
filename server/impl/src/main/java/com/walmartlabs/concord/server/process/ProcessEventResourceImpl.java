package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.api.process.ProcessEventResource;
import com.walmartlabs.concord.server.process.event.EventDao;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class ProcessEventResourceImpl implements ProcessEventResource, Resource {

    private final EventDao eventDao;

    @Inject
    public ProcessEventResourceImpl(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    public List<ProcessEventEntry> list(UUID processInstanceId) {
        return eventDao.list(processInstanceId);
    }
}
