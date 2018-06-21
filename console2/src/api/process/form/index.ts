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
import { ConcordId, fetchJson } from '../../common';

export interface FormRunAs {
    username?: string;
    ldap?: {
        group: string[] | string;
    };
    keep?: boolean;
}

export interface FormListEntry {
    formInstanceId: string;
    name: string;
    custom: boolean;
    yield: boolean;
    runAs?: FormRunAs;
}

export enum Cardinality {
    ONE_OR_NONE = 'ONE_OR_NONE',
    ONE_AND_ONLY_ONE = 'ONE_AND_ONLY_ONE',
    AT_LEAST_ONE = 'AT_LEAST_ONE',
    ANY = 'ANY'
}

export enum FormFieldType {
    STRING = 'string',
    INT = 'int',
    DECIMAL = 'decimal',
    BOOLEAN = 'boolean',
    FILE = 'file'
}

export interface FormField {
    name: string;
    label: string;
    type: FormFieldType;
    cardinality?: Cardinality;
    value?: any;
    allowedValue?: any;
    options?: {
        inputType?: string;
    };
}

export interface FormInstanceEntry {
    processInstanceId: ConcordId;
    formInstanceId: string;
    name: string;
    fields: FormField[];
    custom: boolean;
    yield: boolean;
}

export interface FormSubmitErrors {
    [name: string]: string;
}

export interface FormSubmitResponse {
    ok: boolean;
    processInstanceId: ConcordId;
    errors?: FormSubmitErrors;
}

export const list = (processInstanceId: ConcordId): Promise<FormListEntry[]> =>
    fetchJson(`/api/v1/process/${processInstanceId}/form`);

export const get = (
    processInstanceId: ConcordId,
    formInstanceId: string
): Promise<FormInstanceEntry> =>
    fetchJson(`/api/v1/process/${processInstanceId}/form/${formInstanceId}`);

export const submit = (
    processInstanceId: ConcordId,
    formInstanceId: string,
    values: {}
): Promise<FormSubmitResponse> => {
    const body = new FormData();

    Object.keys(values).forEach((name) => {
        let k = name;
        let v = values[k];

        // special case: a JSON object encoded as a string in a multipart/form-data field
        if (v instanceof Array) {
            v = JSON.stringify(v);
            k = k + '/jsonField';
        }

        if (v !== undefined) {
            body.append(k, v);
        }
    });

    const opts = {
        method: 'POST',
        body
    };

    return fetchJson(`/api/v1/process/${processInstanceId}/form/${formInstanceId}`, opts);
};
