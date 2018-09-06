/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import { Action } from 'redux';

import { ConcordId, ConcordKey, GenericOperationResult, RequestError } from '../../../api/common';
import {
    NewProjectEntry,
    ProjectEntry,
    ProjectOperationResult,
    UpdateProjectEntry
} from '../../../api/org/project';
import {
    EditRepositoryEntry,
    RepositoryValidationResponse
} from '../../../api/org/project/repository';
import { ResourceAccessEntry } from '../../../api/org';
import { RequestState } from '../common';

export interface GetProjectRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

export interface ListProjectsRequest extends Action {
    orgName: ConcordKey;
}

export interface ProjectDataResponse extends Action {
    error?: RequestError;
    items?: ProjectEntry[];
}

export interface CreateProjectRequest extends Action {
    orgName: ConcordKey;
    entry: NewProjectEntry;
}

export interface UpdateProjectRequest extends Action {
    orgName: ConcordKey;
    entry: UpdateProjectEntry;
}

export interface RenameProjectRequest extends Action {
    orgName: ConcordKey;
    projectId: ConcordId;
    projectName: ConcordKey;
}

export interface SetAcceptsRawPayloadRequest extends Action {
    orgName: ConcordKey;
    projectId: ConcordKey;
    acceptsRawPayload: boolean;
}

export interface DeleteProjectRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

export interface AddRepositoryRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    entry: EditRepositoryEntry;
}

export interface UpdateRepositoryRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    entry: EditRepositoryEntry;
}

export interface DeleteRepositoryRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

export interface RefreshRepositoryRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

export interface ValidateRepositoryRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

export interface ProjectTeamAccessRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

export interface UpdateProjectTeamAccessRequest extends Action {
    orgName: ConcordKey;
    projectName: ConcordKey;
    teams: ResourceAccessEntry[];
}

export interface ProjectTeamAccessResponse extends Action {
    error?: RequestError;
    items?: ResourceAccessEntry[];
}

export interface Projects {
    [id: string]: ProjectEntry;
}

export type RenameProjectState = RequestState<ProjectOperationResult>;
export type SetAcceptsRawPayloadState = RequestState<ProjectOperationResult>;
export type DeleteProjectState = RequestState<GenericOperationResult>;
export type CreateRepositoryState = RequestState<GenericOperationResult>;
export type UpdateRepositoryState = RequestState<GenericOperationResult>;
export type DeleteRepositoryState = RequestState<GenericOperationResult>;
export type RefreshRepositoryState = RequestState<GenericOperationResult>;
export type ValidateRepositoryState = RequestState<RepositoryValidationResponse>;
export type updateProjectState = RequestState<ProjectOperationResult>;
export type ProjectTeamAccessState = RequestState<ProjectTeamAccessResponse>;
export type UpdateProjectTeamAccessState = RequestState<ProjectTeamAccessResponse>;

export interface State {
    projectById: Projects;

    // TODO move into a RequestState field
    loading: boolean;
    error: RequestError;

    rename: RenameProjectState;
    acceptRawPayload: SetAcceptsRawPayloadState;
    deleteProject: DeleteProjectState;
    updateProject: updateProjectState;
    projectTeamAccess: ProjectTeamAccessState;
    updateProjectTeamAccess: UpdateProjectTeamAccessState;

    createRepository: CreateRepositoryState;
    updateRepository: UpdateRepositoryState;
    deleteRepository: DeleteRepositoryState;
    refreshRepository: RefreshRepositoryState;
    validateRepository: ValidateRepositoryState;
}
