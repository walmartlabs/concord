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
import type {ConcordId, FetchRange} from "../../types";
import * as common from "../../api";

const str = (s: mixed) => s === undefined ? "" : String(s);

const formatRangeHeader = (range: FetchRange) =>
    ({"Range": `bytes=${str(range.low)}-${str(range.high)}`});

const parseRange = (s: string): FetchRange => {
    const regex = /^bytes (\d*)-(\d*)\/(\d*)$/;
    const m = regex.exec(s);
    if (!m) {
        throw Object({error: true, message: `Invalid Content-Range header: ${s}`});
    }

    return {
        unit: "bytes",
        length: parseInt(m[3], 10),
        low: parseInt(m[1], 10),
        high: parseInt(m[2], 10)
    };
};

const offsetRange = (data: string, range: FetchRange) => {
    // noop, we assume that the data is aligned by \n
    // this will work only with our current implementation of the API
    return {
        data,
        range
    };
};

const defaultFetchRange: FetchRange = {low: undefined, high: 2048};

export const fetchLog = (instanceId: ConcordId, fetchRange: FetchRange = defaultFetchRange) => {
    const rangeHeader = formatRangeHeader(fetchRange);
    console.debug("API: fetchLog ['%s', %o] -> starting...", instanceId, rangeHeader);
    return fetch(`/api/v1/process/${instanceId}/log`, {headers: rangeHeader, credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            const rangeHeader = response.headers.get("Content-Range");
            return response.text().then(data => {
                console.debug("API: fetchLog ['%s', %o] -> done, length: %d", instanceId, fetchRange, data.length);
                return offsetRange(data, parseRange(rangeHeader));
            });
        });
};
