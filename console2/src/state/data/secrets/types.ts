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

import { ConcordKey, GenericOperationResult, RequestError } from '../../../api/common';
import { NewSecretEntry, SecretEntry } from '../../../api/org/secret';
import { RequestState } from '../common';

export interface GetSecretRequest extends Action {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

export interface ListSecretsRequest extends Action {
    orgName: ConcordKey;
}

export interface SecretDataResponse extends Action {
    error?: RequestError;
    items?: SecretEntry[];
}

export interface CreateSecretRequest extends Action {
    orgName: ConcordKey;
    entry: NewSecretEntry;
}

export interface CreateSecretResponse extends Action {
    orgName: ConcordKey;
    name: ConcordKey;
    password?: string;
    publicKey?: string;
}

export interface DeleteSecretRequest extends Action {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

export interface Secrets {
    [id: string]: SecretEntry;
}

export type ListSecretsState = RequestState<{}>;
export type CreateSecretState = RequestState<CreateSecretResponse>;
export type DeleteSecretState = RequestState<GenericOperationResult>;

export interface State {
    secretById: Secrets;

    listSecrets: ListSecretsState;
    createSecret: CreateSecretState;
    deleteSecret: DeleteSecretState;
}
