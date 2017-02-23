// @flow
import ContentRange from "http-range/lib/content-range";
import {sort, log as logConstants} from "./constants";
import type { ConcordId, FetchRange } from "./types";

// utils

const queryParams = (params: {[id: mixed]: string}) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

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

// auth

const authHeader = {"Authorization": "auBy4eDWrKWsyhiDp3AQiw"};

// api functions

const apiListQuery = (name, path) => (sortBy: string, sortDir: string) => {
    console.debug("API: %s ['%s', '%s'] -> starting...", name, sortBy, sortDir);

    const query = queryParams({
        sortBy,
        asc: String(sort.ASC === sortDir)
    });

    return fetch(path + "?" + query, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: %s ['%s', '%s'] -> done, got %d row(s)", name, sortBy, sortDir, json.length);
            return json;
        });
};

export const fetchHistory = apiListQuery("fetchHistory", "/api/v1/history");
export const fetchProjectList = apiListQuery("fetchProjects", "/api/v1/project");
export const fetchTemplateList = apiListQuery("fetchTemplateList", "/api/v1/template");

export const killProc = (id: ConcordId) => {
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

export const fetchProject = (id: ConcordId) => {
    console.debug("API: getProject ['%s'] -> starting...", id);

    return fetch("/api/v1/project/" + id, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: getProject ['%s'] -> done, got %o", id, json);
            return json;
        });
};

export const createProject = (data: Object) => {
    console.debug("API: createProject [%o] -> starting...", data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {headers: Object.assign({}, authHeader, contentType), method: "POST", body: body};

    return fetch("/api/v1/project", opts)
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: createProject [%o] -> done, got %o", data, json);
            return json;
        });
};

export const updateProject = (id: ConcordId, data: Object) => {
    console.debug("API: updateProject ['%s', %o] -> starting...", id, data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {headers: Object.assign({}, authHeader, contentType), method: "PUT", body: body};

    return fetch("/api/v1/project/" + id, opts)
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
        })
        .then(json => {
            console.debug("API: updateProject ['%s', %o] -> done", id, data);
            return json;
        });
};

export const deleteProject = (id: ConcordId) => {
    console.debug("API: deleteProject ['%s'] -> starting...", id);

    return fetch("/api/v1/project/" + id, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: deleteProject ['%s'] -> done", id);
            return true;
        });
};


const offsetRange = (data: string, range: FetchRange) => {
    // if our data starts from the beginning, do nothing
    if (range.low && range.low <= 0) {
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

export const fetchLog = (fileName: string, fetchRange: FetchRange = logConstants.defaultFetchRange) => {
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

export const fetchProcessStatus = (id: ConcordId) => {
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