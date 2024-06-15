package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

public class ProcessCardRequest {

    public static ProcessCardRequest from(MultipartInput input) {
        return new ProcessCardRequest(input);
    }

    private final MultipartInput input;

    private ProcessCardRequest(MultipartInput input) {
        this.input = input;
    }

    @Schema(name = Constants.Multipart.ORG_ID)
    public UUID getOrgId() {
        return MultipartUtils.getUuid(input, Constants.Multipart.ORG_ID);
    }

    @Schema(name = Constants.Multipart.ORG_NAME)
    public String getOrgName() {
        return MultipartUtils.getString(input, Constants.Multipart.ORG_NAME);
    }

    @Schema(name = Constants.Multipart.PROJECT_ID)
    public UUID getProjectId() {
        return MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID);
    }

    @Schema(name = Constants.Multipart.PROJECT_NAME)
    public String getProjectName() {
        return MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME);
    }

    @Schema(name = Constants.Multipart.REPO_ID)
    public UUID getRepoId() {
        return MultipartUtils.getUuid(input, Constants.Multipart.REPO_ID);
    }

    @Schema(name = Constants.Multipart.REPO_NAME)
    public String getRepoName() {
        return MultipartUtils.getString(input, Constants.Multipart.REPO_NAME);
    }

    @Schema(name = Constants.Multipart.ENTRY_POINT)
    public String getEntryPoint() {
        return MultipartUtils.getString(input, Constants.Multipart.ENTRY_POINT);
    }

    @Schema(name = "name")
    public String getName() {
        return MultipartUtils.getString(input, "name");
    }

    @Schema(name = "description")
    public String getDescription() {
        return MultipartUtils.getString(input, "description");
    }

    @Schema(name = "data")
    public Map<String, Object> getData() {
        return MultipartUtils.getMap(input, "data");
    }

    @Schema(name = "id")
    public UUID getId() {
        return MultipartUtils.getUuid(input, "id");
    }

    @Schema(name = "icon")
    public InputStream getIcon() {
        return MultipartUtils.getStream(input, "icon");
    }

    @Schema(name = "form")
    public InputStream getForm() {
        return MultipartUtils.getStream(input, "form");
    }
}
