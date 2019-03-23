/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

export interface RequestErrorData {
    instanceId?: ConcordId;
    message?: string;
    details?: string;
    status: number;
    level?: string;
}

export type RequestError = RequestErrorData | null;

export const parseSiestaError = async (resp: Response) => {
    const json = await resp.json();

    let message;
    if (resp.status < 400 || resp.status >= 500) {
        message = `ERROR: ${resp.statusText} (${resp.status})`;
    }

    return {
        message,
        instanceId: json.instanceId,
        details: json[0].message,
        status: resp.status
    };
};

export const parseJsonError = async (resp: Response) => {
    const json = await resp.json();

    let message;
    if (resp.status < 400 || resp.status >= 500) {
        message = json.message;
    }

    return {
        message,
        instanceId: json.instanceId,
        details: json.details,
        level: json.level ? json.level : 'ERROR',
        status: resp.status
    };
};

export const parseTextError = async (resp: Response) => {
    const text = await resp.text();

    let message;
    if (resp.status < 400 && resp.status >= 500) {
        message = `ERROR: ${resp.statusText} (${resp.status})`;
    }

    return {
        message,
        details: text,
        status: resp.status
    };
};

export const makeError = async (resp: Response): Promise<RequestError> => {
    const contentType = resp.headers.get('Content-Type') || '';
    if (contentType.indexOf('siesta') >= 0) {
        return parseSiestaError(resp);
    } else if (contentType.indexOf('json') >= 0) {
        return parseJsonError(resp);
    } else if (contentType.indexOf('text/plain') >= 0) {
        return parseTextError(resp);
    }

    return {
        message: `ERROR: ${resp.statusText} (${resp.status})`,
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
        console.warn(
            "managedFetch ['%o', '%o'] -> error while performing a request: %o",
            input,
            init,
            response,
            err
        );
        throw { message: 'Error while performing a request', cause: err };
    }

    if (!response.ok) {
        throw await makeError(response);
    }

    return response;
};

/**
 * Generates a query parameter string from an object of key/value pairs
 * @param params the key/value object accepted
 *
 * @return a query parameter string e.g. "foo=123&bar=abc"
 */
export const queryParams = (params: any): string => {
    const esc = encodeURIComponent;
    return Object.keys(params)
        .filter((k) => !!params[k])
        .map((k) => esc(k) + '=' + esc(params[k]))
        .join('&');
};

/**
 * Parse url parameters from a url string
 * @param url a http url string
 *
 * @return an object e.g. { "param": "value", ... }
 */
export const parseQueryParams = (url: string): { [key: string]: string } => {
    // Remove all non-valid characters
    const validString = url.replace(/[^a-z0-9\s-]/, '');

    // Split url and take the right hand side
    // ! assumption there is only one question mark
    let queryParams: string;
    if (validString.includes('?')) {
        queryParams = validString.split('?')[1];
    } else {
        // No Query Params exist in the string
        return {};
    }

    // Split find params by splitting on & characters
    let separateParams: string[] = [];
    if (queryParams.includes('&')) {
        separateParams = queryParams.split('&');
    } else {
        // There is only one param to return, so return it
        const splitPair = queryParams.split('=');
        return { [splitPair[0]]: splitPair[1] };
    }

    // initialize an object for itteration
    let result = {};

    // Inject params as key value pairs
    for (let paramEquality of separateParams) {
        const splitPair = paramEquality.split('=');
        result[splitPair[0]] = splitPair[1];
    }

    return result;
};

export const fetchJson = async <T>(uri: string, init?: RequestInit): Promise<T> => {
    const response = await managedFetch(uri, init);
    return response.json();
};

export interface EntityOwner {
    id: ConcordId;
    username: string;
    displayName?: string;
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
