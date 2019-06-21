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

import {ConcordId, ConcordKey, Owner, RequestError} from '../../../api/common';
import { OrganizationEntry, OrganizationOperationResult } from '../../../api/org';
import { RequestState } from '../common';

export interface ListOrganizationsRequest extends Action {
    onlyCurrent: boolean;
}

export interface ListOrganizationsResponse extends Action {
    error?: RequestError;
    items?: OrganizationEntry[];
}

export interface GetOrganizationRequest extends Action {
    orgName: ConcordKey;
}

export interface ChangeOrganizationOwnerRequest extends Action {
    orgId: ConcordId;
    orgName: ConcordKey;
    owner: Owner;
}

export interface Organizations {
    [id: string]: OrganizationEntry;
}

export type ChangeOwnerState = RequestState<OrganizationOperationResult>;

export interface State {
    orgById: Organizations;
    loading: boolean;
    error: RequestError;
    changeOwner: ChangeOwnerState;
}
