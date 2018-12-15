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
import { ConcordId } from '../../../../api/common';
import { AnsibleHost, AnsibleStatsEntry, SearchFilter } from '../../../../api/process/ansible';
import { RequestState } from '../../common';

export interface ListAnsibleHostsRequest extends Action {
    instanceId: ConcordId;
    filter: SearchFilter;
}

export interface ListAnsibleHostsResponse extends Action {
    data: AnsibleHost[];
    prev?: number;
    next?: number;
}

export interface GetAnsibleStatsRequest extends Action {
    instanceId: ConcordId;
}

export interface GetAnsibleStatsResponse extends Action {
    data: AnsibleStatsEntry;
}

export type AnsibleStatsState = RequestState<GetAnsibleStatsResponse>;
export type AnsibleHostsState = RequestState<ListAnsibleHostsResponse>;

export interface State {
    stats: AnsibleStatsState;
    hosts: AnsibleHostsState;
    lastFilter: SearchFilter;
}
