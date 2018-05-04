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
import { ConcordKey, fetchJson } from '../../common';
import { ProcessEntry } from '../../process';

export const list = (orgName?: ConcordKey, projectName?: ConcordKey): Promise<ProcessEntry[]> => {
    let baseUri = '/api/v1';

    if (orgName) {
        baseUri = `/api/v1/org/${orgName}`;
        if (projectName) {
            baseUri += `/project/${projectName}`;
        }
    }

    return fetchJson(`${baseUri}/process`);
};

export interface StartProcessResponse {
    ok: boolean;
    instanceId: string;
}
