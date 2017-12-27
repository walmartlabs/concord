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

export const testRepository = (data: any): Promise<any> => {
    console.debug("API: testRepository ['%o'] -> starting...", data);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    };

    return fetch("/api/service/console/repository/test", opts)
        .then(response => {
            if (!response.ok) {
                const status = response.status;

                return response.text().then(txt => ({
                    error: true,
                    message: status === 500 ? txt : `${response.statusText} (${status})`
                }));
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: testRepository ['%o'] -> done: %o", data, json);
            return json;
        });
};

export const refreshRepository = (orgName, projectName, repositoryName): Promise<any> => {
    console.debug("API: refreshRepository ['%s', '%s', '%s'] -> starting...", orgName, projectName, repositoryName);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    };

    return fetch(`/api/v1/org/${orgName}/${projectName}/repository/${repositoryName}/refresh`, opts)
        .then(response => {
            if (!response.ok) {
                const status = response.status;

                return response.text().then(txt => ({
                    error: true,
                    message: status === 500 ? txt : `${response.statusText} (${status})`
                }));
            }

            console.debug("API: refreshRepository ['%s', '%s', '%s'] -> done", orgName, projectName, repositoryName);
            return true;
        });
};
