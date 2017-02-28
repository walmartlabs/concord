// @flow
import type {ConcordId} from "../types";
import {authHeader} from "./common";

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
