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

export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const queryParams = (params: { [id: mixed]: string }) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

const errorWithDetails = (resp: any, data: any) => {
    return {
        ...defaultError(resp),
        ...data
    };
};

export const parseError = (resp: any): any => {
    const contentType = resp.headers.get("Content-Type");
    if (isSiestaError(contentType)) {
        return resp.json().then(json => {
            const data = json.length > 0 ? json[0] : {};
            throw errorWithDetails(resp, data);
        });
    } else if (isJson(contentType)) {
        return resp.json().then(json => {
            throw errorWithDetails(resp, json);
        });
    }

    return new Promise(() => {
        throw defaultError(resp);
    });
};

const isJson = (h: ?string): boolean => {
    if (!h) {
        return false;
    }

    if (h.indexOf("application/json") !== -1) {
        return true;
    }
    return false;
};

const isSiestaError = (h: ?string): boolean => {
    if (!h) {
        return false;
    }

    if (h.indexOf("application/vnd.siesta-validation-errors-v1+json") !== -1) {
        return true;
    }

    return false;
};

export const defaultError = (resp: any) => {
    return {
        status: resp.status,
        message: `ERROR: ${resp.statusText} (${resp.status})`
    };
};
