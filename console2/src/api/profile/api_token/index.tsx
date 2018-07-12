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

import { fetchJson, GenericOperationResult, ConcordId, ConcordKey } from '../../common';

export interface TokenEntry {
    id: ConcordId;
    name: ConcordKey;
    expiredAt: string;
}

export interface NewTokenEntry {
    name: ConcordKey;
}

export interface CreateApiKeyResult {
    ok: boolean;
    id: string;
    key: string;
    expiredAt: string;
}

export const list = (): Promise<TokenEntry[]> => fetchJson(`/api/v1/apikey`);

export const create = (entry: NewTokenEntry): Promise<CreateApiKeyResult> => {
    const obj: RequestInit = {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
    };

    return fetchJson('/api/v1/apikey', obj);
};

export const deleteToken = (id: ConcordId): Promise<GenericOperationResult> =>
    fetchJson(`/api/v1/apikey/${id}`, { method: 'DELETE' });
