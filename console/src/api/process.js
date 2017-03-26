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
