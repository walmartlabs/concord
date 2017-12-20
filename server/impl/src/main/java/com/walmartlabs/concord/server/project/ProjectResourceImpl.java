package com.walmartlabs.concord.server.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.api.org.project.EncryptValueResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectOperationResponse;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.UUID;

@Named
@Deprecated
public class ProjectResourceImpl implements ProjectResource, Resource {

    private final com.walmartlabs.concord.server.api.org.project.ProjectResource orgProjects;

    @Inject
    public ProjectResourceImpl(com.walmartlabs.concord.server.api.org.project.ProjectResource orgProjects) {
        this.orgProjects = orgProjects;
    }

    @Override
    @Validate
    public CreateProjectResponse createOrUpdate(ProjectEntry req) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        ProjectOperationResponse resp = orgProjects.createOrUpdate(orgName, req);
        return new CreateProjectResponse(resp.getId(), resp.getResult());
    }

    @Override
    @Validate
    public CreateRepositoryResponse createRepository(String projectName, RepositoryEntry request) {
        throw new WebApplicationException("Removed. Please use the new Organizations API.", Status.GONE);
    }

    @Override
    @Validate
    public ProjectEntry get(String projectName) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        return orgProjects.get(orgName, projectName);
    }

    @Override
    @Validate
    public RepositoryEntry getRepository(String projectName, String repositoryName) {
        throw new WebApplicationException("Removed. Please use the new Organizations API.", Status.GONE);
    }

    @Override
    @Validate
    public List<ProjectEntry> list(UUID orgId, String sortBy, boolean asc) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        return orgProjects.list(orgName);
    }

    @Override
    @Validate
    public List<RepositoryEntry> listRepositories(String projectName, String sortBy, boolean asc) {
        throw new WebApplicationException("Removed. Please use the new Organizations API.", Status.GONE);
    }

    @Override
    @Validate
    public UpdateRepositoryResponse updateRepository(String projectName, String repositoryName, RepositoryEntry request) {
        throw new WebApplicationException("Removed. Please use the new Organizations API.", Status.GONE);
    }

    @Override
    @Validate
    @SuppressWarnings("unchecked")
    public Object getConfiguration(String projectName, String path) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        return orgProjects.getConfiguration(orgName, projectName, path);
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, String path, Object data) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        orgProjects.updateConfiguration(orgName, projectName, path, data);
        return new UpdateProjectConfigurationResponse();
    }

    @Override
    @Validate
    public UpdateProjectConfigurationResponse updateConfiguration(String projectName, Object data) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        orgProjects.updateConfiguration(orgName, projectName, data);
        return new UpdateProjectConfigurationResponse();
    }

    @Override
    public DeleteProjectConfigurationResponse deleteConfiguration(String projectName, String path) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        orgProjects.deleteConfiguration(orgName, projectName, path);
        return new DeleteProjectConfigurationResponse();
    }

    @Override
    @Validate
    public DeleteProjectResponse delete(String projectName) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        orgProjects.delete(orgName, projectName);
        return new DeleteProjectResponse();
    }

    @Override
    @Validate
    public DeleteRepositoryResponse deleteRepository(String projectName, String repositoryName) {
        throw new WebApplicationException("Removed. Please use the new Organizations API.", Status.GONE);
    }

    @Override
    @Validate
    @RequiresAuthentication
    public EncryptValueResponse encrypt(String projectName, EncryptValueRequest req) {
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        return orgProjects.encrypt(orgName, projectName, req.getValue());
    }
}
