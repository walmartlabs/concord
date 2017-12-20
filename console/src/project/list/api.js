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
import type {ConcordKey} from "../../types";
import * as common from "../../api";

export const listProjects = (orgName: ConcordKey, sortBy: string = "name", sortDir: string = common.sort.ASC): Promise<any> => {
    console.debug("API: listProjects ['%s', '%s', '%s'] -> starting...", orgName, sortBy, sortDir);

    const query = common.queryParams({
        sortBy,
        asc: String(common.sort.ASC === sortDir)
    });

    return fetch(`/api/v1/org/${orgName}/project?${query}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listProjects ['%s', '%s', '%s'] -> done, got %d row(s)", orgName, sortBy, sortDir, json.length);
            return json;
        });
};
