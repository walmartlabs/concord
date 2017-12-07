package com.walmartlabs.concord.server.org;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.CreateOrganizationResponse;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

@Named
public class OrganizationResourceImpl implements OrganizationResource, Resource {

    private final OrganizationDao orgDao;
    private final OrganizationManager orgManager;

    @Inject
    public OrganizationResourceImpl(OrganizationDao orgDao, OrganizationManager orgManager) {
        this.orgDao = orgDao;
        this.orgManager = orgManager;
    }

    @Override
    public CreateOrganizationResponse createOrUpdate(OrganizationEntry entry) {
        UUID orgId = entry.getId();
        if (orgId != null) {
            orgDao.update(orgId, entry.getName());
            return new CreateOrganizationResponse(orgId, OperationResult.UPDATED);
        } else {
            orgId = orgManager.create(entry);
            return new CreateOrganizationResponse(orgId, OperationResult.CREATED);
        }
    }

    @Override
    public OrganizationEntry get(String orgName) {
        return orgDao.getByName(orgName);
    }

    @Override
    public List<OrganizationEntry> list() {
        return orgDao.list();
    }
}
