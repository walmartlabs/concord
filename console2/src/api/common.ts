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

export interface Owner {
    username: string;
    userDomain?: string;
}

export interface RequestErrorData {
    instanceId?: ConcordId;
    message?: string;
    details?: string;
    status: number;
    level?: string;
}

export type RequestError = RequestErrorData | null;


const statusMessage = (message: unknown, resp: Pick<Response, 'status' | 'statusText'>): string => {
    const value =
        typeof message === 'string' && message.trim().length > 0
            ? message.trim()
            : resp.statusText || 'Request failed';
    return `${value} (${resp.status})`;
};

export const toRequestError = (error: unknown): RequestErrorData => {
    if (typeof error === 'object' && error !== null) {
        const value = error as Partial<RequestErrorData>;
        if (typeof value.status === 'number') {
            return {
                status: value.status,
                ...(typeof value.message === 'string' ? { message: value.message } : {}),
                ...(typeof value.details === 'string' ? { details: value.details } : {}),
                ...(typeof value.instanceId === 'string' ? { instanceId: value.instanceId } : {}),
                ...(typeof value.level === 'string' ? { level: value.level } : {}),
            };
        }
    }

    if (error instanceof Error) {
        const message = error.message.trim() || error.name || 'Request failed';
        return {
            status: 0,
            message: `${message} (0)`,
            details: message,
        };
    }

    let details: string;
    if (typeof error === 'string') {
        details = error;
    } else if (
        error === null ||
        typeof error === 'number' ||
        typeof error === 'boolean' ||
        typeof error === 'bigint' ||
        typeof error === 'undefined'
    ) {
        details = `${error}`;
    } else {
        try {
            const value = JSON.stringify(error);
            details = value === undefined ? 'Unknown error' : value;
        } catch {
            details = 'Unknown error';
        }
    }
    return {
        status: 0,
        message: 'Request failed (0)',
        details,
    };
};

export const parseSiestaError = async (resp: Response): Promise<RequestErrorData> => {
    const json = await resp.json();
    const bodyMessage =
        (typeof json?.message === 'string' && json.message) ||
        (Array.isArray(json) && typeof json[0]?.message === 'string' && json[0].message);

    return {
        message: statusMessage(bodyMessage, resp),
        instanceId: json.instanceId,
        details: Array.isArray(json) ? json[0]?.message : json.details,
        status: resp.status,
    };
};

export const parseJsonError = async (resp: Response): Promise<RequestErrorData> => {
    const json = await resp.json();

    return {
        message: statusMessage(json.message, resp),
        instanceId: json.instanceId,
        details: json.details,
        level: json.level ? json.level : 'ERROR',
        status: resp.status,
    };
};

export const parseTextError = async (resp: Response): Promise<RequestErrorData> => {
    const text = await resp.text();

    return {
        message: statusMessage(text, resp),
        details: text,
        status: resp.status,
    };
};

export const makeError = async (resp: Response): Promise<RequestErrorData> => {
    const contentLength = resp.headers.get('Content-Length');
    if (contentLength !== '0') {
        const contentType = resp.headers.get('Content-Type') || '';
        try {
            if (contentType.indexOf('vnd.concord-validation-errors-v1+json') >= 0) {
                return parseSiestaError(resp);
            } else if (contentType.indexOf('json') >= 0) {
                return parseJsonError(resp);
            } else if (contentType.indexOf('text/plain') >= 0) {
                return parseTextError(resp);
            }
        } catch (e) {
            console.warn('makeError -> error while parsing the response: %o', e);
            // fall back to the default error handling
        }
    }

    return {
        message: statusMessage(undefined, resp),
        status: resp.status,
    };
};

export const managedFetch = async (input: RequestInfo, init?: RequestInit): Promise<Response> => {
    if (!init) {
        init = {};
    }

    init.credentials = 'same-origin';

    if (!init.headers) {
        init.headers = new Headers();
    }

    // send a special header with each request to indicate that this is, in fact, a UI request
    if (init.headers instanceof Headers) {
        init.headers.set('X-Concord-UI-Request', 'true');
    } else {
        init.headers = { ...init.headers, 'X-Concord-UI-Request': 'true' };
    }

    let response;
    try {
        response = await fetch(input, init);
    } catch (err) {
        if (
            (err instanceof Error || err instanceof DOMException) &&
            err.name === 'AbortError'
        ) {
            throw err;
        }

        console.warn(
            "managedFetch ['%o', '%o'] -> error while performing a request: %o",
            input,
            init,
            err
        );
        throw toRequestError(err);
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
export const queryParams = (params: any, allowEmpty?: boolean): string => {
    const esc = encodeURIComponent;
    const result: string[] = [];

    Object.keys(params)
        .filter((k) => {
            const v = params[k];

            if (v === undefined || v === null || (!allowEmpty && v === '')) {
                return false;
            }

            if (Array.isArray(v) && v.length === 0) {
                return false;
            }

            return true;
        })
        .forEach((k) => {
            const v = params[k];
            if (Array.isArray(v)) {
                v.forEach((vv) => {
                    result.push(esc(k) + '=' + esc(vv));
                });
            } else {
                result.push(esc(k) + '=' + esc(v));
            }
        });

    return result.join('&');
};

export type QueryParams = { [key: string]: string };

/**
 * Parse url parameters from a url string
 * @param url a http url string
 *
 * @return an object e.g. { "param": "value", ... }
 */
export const parseQueryParams = (url: string): QueryParams => {
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
    let kvs: string[] = [];
    if (queryParams.includes('&')) {
        kvs = queryParams.split('&');
    } else {
        // There is only one param to return, so return it
        const [k, v] = queryParams.split('=');
        return { [k]: v };
    }

    // initialize an object for iteration
    let result = {};

    // inject params as key value pairs
    for (const kv of kvs) {
        const [k, v] = kv.split('=');

        // handle multi-value params
        const prev = result[k];
        if (prev) {
            if (prev instanceof Array) {
                prev.push(v);
            } else {
                result[k] = [prev, v];
            }
        } else {
            result[k] = v;
        }
    }

    return result;
};

export const deepMerge = (a: any, b: any): any => {
    const result = { ...a };

    Object.keys(b).forEach((k) => {
        const av = a[k];
        const bv = b[k];

        let o = bv;
        if (typeof av === 'object' && typeof bv === 'object') {
            o = deepMerge(av, bv);
        }

        result[k] = o;
    });

    return result;
};

export type QueryMultiParams = { [key: string]: any };

export const parseNestedQueryParams = (params: QueryParams, keys: string[]): QueryMultiParams => {
    let result: QueryMultiParams = { ...params };

    Object.keys(params)
        .filter((p) => keys.some((k) => p.startsWith(k) && p.includes('.')))
        .forEach((p) => {
            let as = p.split('.').reverse();

            let obj: QueryMultiParams = { [as[0]]: params[p] };
            for (let i = 1; i < as.length; i++) {
                obj = { [as[i]]: obj };
            }

            delete result[p];
            result = deepMerge(result, obj);
        });

    return result;
};

export const fetchJson = async <T>(uri: string, init?: RequestInit): Promise<T> => {
    const response = await managedFetch(uri, init);
    return response.json();
};

const wait = (delayMs: number) =>
    new Promise<void>((resolve) => {
        setTimeout(resolve, delayMs);
    });

export const retryRequest = async <T>(
    request: () => Promise<T>,
    shouldRetry: (error: RequestError) => boolean,
    attempts = 5,
    delayMs = 250
): Promise<T> => {
    let lastError: RequestError;

    for (let attempt = 1; attempt <= attempts; attempt++) {
        try {
            return await request();
        } catch (e) {
            lastError = e as RequestError;

            if (attempt === attempts || !shouldRetry(lastError)) {
                throw e;
            }

            await wait(delayMs);
        }
    }

    throw lastError;
};

export interface EntityOwner {
    id: ConcordId;
    username: string;
    userDomain?: string;
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

export enum EntityType {
    PROJECT = 'PROJECT',
    SECRET = 'SECRET'
}
