// @flow
import type {ConcordId, FetchRange} from "../../types";
import ContentRange from "http-range/lib/content-range";
import * as common from "../../api";

const str = (s: mixed) => s === undefined ? "" : String(s);

const formatRangeHeader = (range: FetchRange) =>
    ({"Range": `bytes=${str(range.low)}-${str(range.high)}`});

const parseRange = (s: string): FetchRange => {
    const range = ContentRange.prototype.parse(s);
    return {
        unit: range.unit,
        length: range.length,
        low: range.range.low,
        high: range.range.high
    };
};

const offsetRange = (data: string, range: FetchRange) => {
    // if our data starts from the beginning, do nothing
    if (range.low !== undefined && range.low <= 0) {
        return {data, range};
    }

    // seek to the nearest newline
    const i = data.indexOf("\n");

    // no line breaks or a single line, do nothing
    if (i < 0 || i + 1 >= data.length) {
        return {data, range};
    }

    // trim data to a new line, add trimmed offest to the start of a range
    return {
        data: data.substr(i + 1),
        range: {...range, low: range.low + i + 1}
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
