import ContentRange from "http-range/lib/content-range";
import {sort, log as logConstants} from "./constants";

// utils

const queryParams = (params) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

const str = (s) => s === undefined ? "" : s;

const formatRangeHeader = (range) =>
    ({"Range": `bytes=${str(range.low)}-${str(range.high)}`});

const parseRange = (s) => {
    if (!s) {
        return null;
    }
    const range = ContentRange.prototype.parse(s);
    return {
        unit: range.unit,
        length: range.length,
        low: range.range.low,
        high: range.range.high
    };
};

// auth

const authHeader = {"Authorization": "auBy4eDWrKWsyhiDp3AQiw"};

// api functions

export const fetchHistory = (sortBy, sortDir) => {
    console.debug("API: fetchHistory ['%s', '%s'] -> starting...", sortBy, sortDir);

    const query = queryParams({
        sortBy,
        asc: sort.ASC === sortDir
    });

    return fetch("/api/v1/history?" + query, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchHistory ['%s', '%s'] -> done, got %d row(s)", sortBy, sortDir, json.length);
            return json;
        });
};

export const killProc = (id) => {
    console.debug("API: killProc ['%s'] -> starting...", id);

    return fetch("/api/v1/process/" + id, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: killProc ['%s'] -> done", id);
            return true;
        });
};

const offsetRange = (data, range) => {
    // if our data starts from the beginning, do nothing
    if (range.low <= 0) {
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

export const fetchLog = (fileName, fetchRange = logConstants.defaultFetchRange) => {
    const rangeHeader = formatRangeHeader(fetchRange);
    console.debug("API: fetchLog ['%s', %o] -> starting...", fileName, rangeHeader);
    return fetch("/logs/" + fileName, {headers: Object.assign({}, authHeader, rangeHeader)})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }

            const rangeHeader = response.headers.get("Content-Range");
            return response.text().then(data => {
                console.debug("API: fetchLog ['%s', %o] -> done, length: %d", fileName, fetchRange, data.length);
                return offsetRange(data, parseRange(rangeHeader));
            });
        });
};

export const fetchProcessStatus = (id) => {
    console.debug("API: fetchProcessStatus ['%s'] -> starting...", id);
    return fetch("/api/v1/process/" + id, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: fetchProcessStatus ['%s'] -> done: %o", id, json);
            return json;
        });
};