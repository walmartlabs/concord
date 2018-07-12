/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { ConcordId, GenericOperationResult, RequestError } from '../../../api/common';
import { NewTokenEntry, TokenEntry } from '../../../api/profile/api_token';
import { RequestState } from '../common';

export interface TokenDataResponse extends Action {
    error?: RequestError;
    items?: TokenEntry[];
}

export interface CreateTokenRequest extends Action {
    entry: NewTokenEntry;
}

export interface CreateTokenResponse extends Action {
    id: ConcordId;
    key: string;
}

export interface DeleteTokenRequest extends Action {
    id: ConcordId;
}

export interface Tokens {
    [id: string]: TokenEntry;
}

export type ListTokensState = RequestState<{}>;
export type CreateTokenState = RequestState<CreateTokenResponse>;
export type DeleteTokenState = RequestState<GenericOperationResult>;

export interface State {
    tokenById: Tokens;

    loading: boolean;
    error: RequestError;

    listTokens: ListTokensState;
    createToken: CreateTokenState;
    deleteToken: DeleteTokenState;
}
