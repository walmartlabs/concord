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
// @flow
import type { ConcordId } from '../../types';
import * as common from '../../api';

export const startProject = (repositoryId: ConcordId): Promise<any> => {
    console.debug("API: startProject ['%s'] -> starting...", repositoryId);

    const data = new FormData();
    data.append('repoId', repositoryId);

    const opts = {
        method: 'POST',
        credentials: 'same-origin',
        body: data
    };

    return fetch(`api/v1/process`, opts)
        .then((response) => {
            if (!response.ok) {
                return common.parseError(response);
            }

            return response.json();
        })
        .then((json) => {
            console.debug("API: startProject ['%s'] -> done: %o", repositoryId, json);
            return json;
        });
};
