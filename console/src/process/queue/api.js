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
import * as common from '../../api';

export const loadData = (
    sortBy: string = 'lastUpdatedAt',
    sortDir: string = common.sort.DESC,
    projectId: string = '',
    org: string,
    beforeCreatedAt: string,
    tags: string[],
    limit: string = '30'
): Promise<any> => {
    console.debug(
        "API: loadData ['%s', '%s', '%s', '%s', '%s'] -> starting...",
        sortBy,
        sortDir,
        projectId,
        org,
        limit
    );

    const query = common.queryParams({
        sortBy,
        asc: String(common.sort.ASC === sortDir),
        limit
    });

    return fetch(`/api/v1/process?${query}`, { credentials: 'same-origin' })
        .then((response) => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug(
                "API: loadData ['%s', '%s'] -> done, got %d row(s)",
                sortBy,
                sortDir,
                json.length
            );
            return json;
        });
};

export const fetchProjectProcesses = (projectId: string): Promise<any> => {
    console.debug("API: fetchProjectProcesses ['%s'] -> starting...", projectId);

    const query = common.queryParams({
        projectId
    });

    //TODO: incrementally query the processes and remove hardcoded limit
    return fetch(`/api/v1/process?${query}&limit=100`, { credentials: 'same-origin' })
        .then((response) => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug(
                "API: fetchProjectProcesses ['%s'] -> done, got %d row(s)",
                projectId,
                json.length
            );
            return json;
        });
};
