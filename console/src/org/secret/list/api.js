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
import type { ConcordKey } from '../../../types';
import * as common from '../../../api';

export const fetchSecretList = (orgName: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret`, { credentials: 'same-origin' })
        .then((response) => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            return json;
        });
};

export const deleteSecret = (orgName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret/${name}`, {
        method: 'DELETE',
        credentials: 'same-origin'
    }).then((response) => {
        if (!response.ok) {
            throw new Error('ERROR: ' + response.statusText + ' (' + response.status + ')');
        }
        return true;
    });
};

export const getPublicKey = (orgName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret/${name}/public`, {
        method: 'GET',
        credentials: 'same-origin'
    }).then((response) => {
        if (!response.ok) {
            throw new Error('ERROR: ' + response.statusText + ' (' + response.status + ')');
        }
        return response.json();
    });
};
