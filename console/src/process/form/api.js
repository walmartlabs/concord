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
import { defaultError } from '../../api';

export const listForms = (instanceId: ConcordId): Promise<any> => {
    console.debug("API: listForms ['%s'] -> starting...", instanceId);
    return fetch(`/api/v1/process/${instanceId}/form`, { credentials: 'same-origin' })
        .then((response) => {
            if (!response.ok) {
                throw defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug("API: listForms ['%s'] -> done, got %o", instanceId, json);
            return json;
        });
};

export const fetchForm = (instanceId: ConcordId, formInstanceId: ConcordId): Promise<any> => {
    console.debug("API: fetchForm ['%s', '%s'] -> starting...", instanceId, formInstanceId);
    return fetch(`/api/v1/process/${instanceId}/form/${formInstanceId}`, {
        credentials: 'same-origin'
    })
        .then((response) => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug(
                "API: fetchForm ['%s', '%s'] -> done, got %o",
                instanceId,
                formInstanceId,
                json
            );
            return json;
        });
};

export const submitForm = (
    instanceId: ConcordId,
    formInstanceId: ConcordId,
    data: any
): Promise<any> => {
    console.debug(
        "API: submitForm ['%s', '%s', %o] -> starting...",
        instanceId,
        formInstanceId,
        data
    );

    const formData = new FormData();
    for (const name in data) {
        let key = name;
        let x = data[name];

        // special case: a JSON object encoded as a string in a multipart/form-data field
        if (x instanceof Array) {
            x = JSON.stringify(x);
            key = key + '/jsonField';
        }

        if (x !== undefined) {
            formData.append(key, x);
        }
    }

    const body = formData;
    const opts = {
        method: 'POST',
        credentials: 'same-origin',
        body: body
    };

    return fetch(`/api/v1/process/${instanceId}/form/${formInstanceId}`, opts)
        .then((response) => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug(
                "API: submitForm ['%s','%s', %o] -> done, got %o",
                instanceId,
                formInstanceId,
                data,
                json
            );
            return json;
        });
};

export const startSession = (instanceId: ConcordId, formInstanceId: ConcordId): Promise<any> => {
    console.debug("API: startSession ['%s', '%s'] -> starting...", instanceId, formInstanceId);

    const opts = {
        method: 'POST',
        credentials: 'same-origin',
        redirect: 'manual'
    };

    return fetch(`/api/service/custom_form/${instanceId}/${formInstanceId}/start`, opts)
        .then((response) => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            console.debug(
                "API: startSession ['%s', '%s'] -> done, got: %o",
                instanceId,
                formInstanceId,
                json
            );
            return json;
        });
};
