// @flow
import type {ConcordId} from "../types";
import {authHeader, defaultError} from "./common";

export const killProc = (id: ConcordId) => {
    console.debug("API: killProc ['%s'] -> starting...", id);

    return fetch(`/api/v1/process/${id}`, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            console.debug("API: killProc ['%s'] -> done", id);
            return true;
        });
};

export const fetchStatus = (id: ConcordId) => {
    console.debug("API: fetchStatus ['%s'] -> starting...", id);
    return fetch(`/api/v1/process/${id}`, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: fetchStatus ['%s'] -> done: %o", id, json);
            return json;
        });
};

export const start = (entryPoint: string) => {
    console.debug("API: start ['%s'] -> starting...", entryPoint);

    const body = JSON.stringify({});
    const contentType = {"Content-Type": "application/json"};
    const opts = {
        method: "POST",
        headers: Object.assign({}, authHeader, contentType),
        body: body
    };

    return fetch(`/api/v1/process/${entryPoint}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: start ['%s'] -> done, got %o", entryPoint, json);
            return json;
        });
};