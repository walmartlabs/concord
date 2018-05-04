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

export type ConcordId = string;
export type ConcordKey = string;

interface RequestErrorData {
    message?: string;
    details?: string;
    status: number;
}

export type RequestError = RequestErrorData | null;

export const makeError = async (resp: Response): Promise<RequestError> => {
    const message = `ERROR: ${resp.statusText} (${resp.status})`;

    const contentType = resp.headers.get('Content-Type') || '';
    if (contentType.indexOf('json') >= 0) {
        const json = await resp.json();
        return {
            message,
            details: json[0].message,
            status: resp.status
        };
    } else if (contentType.indexOf('text/plain') >= 0) {
        const text = await resp.text();
        return {
            message,
            details: text,
            status: resp.status
        };
    }

    return {
        message,
        status: resp.status
    };
};

export const managedFetch = async (input: RequestInfo, init?: RequestInit): Promise<Response> => {
    if (!init) {
        init = {};
    }

    init.credentials = 'same-origin';

    let response;
    try {
        response = await fetch(input, init);
    } catch (err) {
        console.debug(
            "managedFetch ['%o', '%o'] -> error while performing a request: %o",
            input,
            init,
            response
        );
        throw { message: 'Error while performing a request', cause: err };
    }

    if (!response.ok) {
        throw await makeError(response);
    }

    return response;
};

export const queryParams = (params: object) => {
    const esc = encodeURIComponent;
    return Object.keys(params)
        .filter((k) => !!params[k])
        .map((k) => esc(k) + '=' + esc(params[k]))
        .join('&');
};

export const fetchJson = async <T>(uri: string, init?: RequestInit): Promise<T> => {
    const response = await managedFetch(uri, init);
    return response.json();
};

export interface Owner {
    id: ConcordId;
    username: string;
}

export enum OperationResult {
    CREATED = 'CREATED',
    UPDATED = 'UPDATED',
    DELETED = 'DELETED',
    ALREADY_EXISTS = 'ALREADY_EXISTS',
    NOT_FOUND = 'NOT_FOUND'
}

export interface GenericOperationResult {
    ok: boolean;
    result: OperationResult;
}
