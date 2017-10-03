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
        throw Object({error: true, message: ``});
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
