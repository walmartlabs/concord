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

import { ConcordKey, RequestError } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';
import { RequestState } from '../common';

// TODO should it be a common type?
// TODO rename "offset" to "page"?
export interface Pagination {
    limit: number;
    offset: number;
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

export type ProjectTeamAccessState = RequestState<ProjectTeamAccessResponse>;
export type UpdateProjectTeamAccessState = RequestState<ProjectTeamAccessResponse>;

export interface State {
    projectTeamAccess: ProjectTeamAccessState;
    updateProjectTeamAccess: UpdateProjectTeamAccessState;
}
