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
import { secretTypes, storePwdTypes } from './constants';

export const exists = (orgName: ConcordKey, secretName: ConcordKey): any => {
    return fetch(`/api/service/console/org/${orgName}/secret/${secretName}/exists`, {
        credentials: 'same-origin'
    }).then((response) => {
        if (!response.ok) {
            throw new common.defaultError(response);
        }

        return response.json();
    });
};

export const create = ({
    orgName,
    name,
    visibility,
    secretType,
    storePwdType,
    storePassword,
    storeType,
    ...rest
}: any): any => {
    const data = new FormData();

    data.append('name', name);

    data.append('storeType', storeType);

    data.append('visibility', visibility);

    switch (secretType) {
        case secretTypes.newKeyPair: {
            data.append('type', 'KEY_PAIR');
            break;
        }
        case secretTypes.existingKeyPair: {
            if (!rest.publicFile || !rest.privateFile) {
                return Promise.reject(new Error('Missing public and/or private key files'));
            }

            data.append('type', 'KEY_PAIR');
            data.append('public', rest.publicFile[0]);
            data.append('private', rest.privateFile[0]);
            break;
        }
        case secretTypes.usernamePassword: {
            if (!rest.username || !rest.password) {
                return Promise.reject(new Error('Missing username and/or password values'));
            }

            data.append('type', 'USERNAME_PASSWORD');
            data.append('username', rest.username);
            data.append('password', rest.password);
            break;
        }
        case secretTypes.singleValue: {
            if (!rest.data) {
                return Promise.reject(new Error('Missing the data value'));
            }

            data.append('type', 'DATA');
            data.append('data', rest.data);
            break;
        }
        default: {
            return Promise.reject(`Unsupported secret type: ${secretType}`);
        }
    }

    switch (storePwdType) {
        case storePwdTypes.doNotUse: {
            break;
        }
        case storePwdTypes.specify: {
            if (!storePassword) {
                return Promise.reject(new Error('Missing the store password value'));
            }

            data.append('storePassword', storePassword);
            break;
        }
        case storePwdTypes.generate: {
            data.append('generatePassword', 'true');
            break;
        }
        default: {
            return Promise.reject(`Unsupported store password type: ${storePwdType}`);
        }
    }

    let opts = {
        method: 'POST',
        credentials: 'same-origin',
        body: data
    };

    return fetch(`/api/v1/org/${orgName}/secret`, opts).then((resp) => {
        if (!resp.ok) {
            return common.parseError(resp);
        }
        return resp.json();
    });
};
