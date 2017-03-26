// @flow
import type {FetchRange} from "../types";
import ContentRange from "http-range/lib/content-range";
import {sort} from "../constants";

export const authHeader = {"Authorization": "auBy4eDWrKWsyhiDp3AQiw"};

export const queryParams = (params: { [id: mixed]: string }) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

export const str = (s: mixed) => s === undefined ? "" : String(s);

export const formatRangeHeader = (range: FetchRange) =>
    ({"Range": `bytes=${str(range.low)}-${str(range.high)}`});

export const parseRange = (s: string): FetchRange => {
    const range = ContentRange.prototype.parse(s);
    return {
        unit: range.unit,
        length: range.length,
        low: range.range.low,
        high: range.range.high
    };
};

export const defaultError = (resp: any) => {
    return new Error(`ERROR: ${resp.statusText} (${resp.status})`);
};

export const apiListQuery = (name: string, path: string) => (sortBy: string, sortDir: string) => {
    console.debug("API: %s ['%s', '%s'] -> starting...", name, sortBy, sortDir);

    const query = queryParams({
        sortBy,
        asc: String(sort.ASC === sortDir)
    });

    return fetch(path + "?" + query, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: %s ['%s', '%s'] -> done, got %d row(s)", name, sortBy, sortDir, json.length);
            return json;
        });
};

export const delay = (ms: number, data: any) => new Promise((resolve, reject) =>
    setTimeout(() => resolve(data), ms));

